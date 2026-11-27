package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.movie.model.CatalogImportColumn;
import com.cinema.modules.movie.model.CatalogImportLayout;
import com.cinema.modules.movie.model.CatalogImportRow;
import com.cinema.modules.movie.util.ExcelSheets;
import com.cinema.modules.movie.util.ExcelStyles;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CatalogImportWorkbook {

    static final int MAX_ROWS = 500;
    private static final String ERROR_HEADER = "Lỗi";
    private static final String GUIDE_SHEET_NAME = "Hướng dẫn";
    private static final int ERROR_COLUMN_WIDTH = 70;
    private static final int GUIDE_COLUMN_WIDTH = 100;

    public List<CatalogImportRow> read(byte[] content, CatalogImportLayout catalogImportLayout) {
        try (InputStream inputStream = new ByteArrayInputStream(content);
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheet(catalogImportLayout.sheetName()) != null
                    ? workbook.getSheet(catalogImportLayout.sheetName())
                    : workbook.getSheetAt(0);
            return readRows(sheet, catalogImportLayout.columns());
        } catch (AppException appException) {
            throw appException;
        } catch (IOException | RuntimeException exception) {
            throw new AppException(
                    "Không đọc được tệp Excel, hãy dùng tệp mẫu định dạng .xlsx",
                    HttpStatus.BAD_REQUEST,
                    "INVALID_IMPORT_FILE");
        }
    }

    public byte[] writeTemplate(CatalogImportLayout catalogImportLayout, List<String> existingNames) {
        List<CatalogImportRow> sampleRows = catalogImportLayout.sampleRows().stream()
                .map(values -> new CatalogImportRow(0, values))
                .toList();
        return write(catalogImportLayout, sampleRows, Map.of(), existingNames, true);
    }

    public byte[] writeRejectedRows(
            CatalogImportLayout catalogImportLayout,
            List<CatalogImportRow> catalogImportRows,
            Map<Integer, List<String>> errorsByRowNumber,
            List<String> existingNames) {
        return write(catalogImportLayout, catalogImportRows, errorsByRowNumber, existingNames, false);
    }

    private List<CatalogImportRow> readRows(Sheet sheet, List<CatalogImportColumn> columns) {
        int[] columnIndexes = findColumnIndexes(sheet.getRow(sheet.getFirstRowNum()), columns);
        List<CatalogImportRow> catalogImportRows = new ArrayList<>();
        for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            List<String> values = new ArrayList<>();
            for (int columnIndex : columnIndexes) {
                values.add(columnIndex < 0 ? "" : ExcelSheets.readCell(row.getCell(columnIndex)));
            }
            if (values.stream().allMatch(String::isEmpty) || ExcelSheets.isSample(values.getFirst())) {
                continue;
            }
            if (catalogImportRows.size() == MAX_ROWS) {
                throw new AppException(
                        "Mỗi lần nhập tối đa " + MAX_ROWS + " dòng, hãy tách tệp thành nhiều phần",
                        HttpStatus.BAD_REQUEST,
                        "TOO_MANY_IMPORT_ROWS");
            }
            catalogImportRows.add(new CatalogImportRow(rowIndex + 1, values));
        }
        if (catalogImportRows.isEmpty()) {
            throw new AppException(
                    "Tệp chưa có dòng dữ liệu nào. Các dòng bắt đầu bằng [Mẫu] không được nhập",
                    HttpStatus.BAD_REQUEST,
                    "EMPTY_IMPORT_FILE");
        }
        return catalogImportRows;
    }

    private int[] findColumnIndexes(Row headerRow, List<CatalogImportColumn> columns) {
        int[] columnIndexes = new int[columns.size()];
        List<String> missingHeaders = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            columnIndexes[i] = findColumnIndex(headerRow, columns.get(i).header());
            if (columnIndexes[i] < 0 && columns.get(i).required()) {
                missingHeaders.add(columns.get(i).header());
            }
        }
        if (!missingHeaders.isEmpty()) {
            throw new AppException(
                    "Tệp thiếu cột: " + String.join(", ", missingHeaders) + ". Hãy dùng tệp mẫu",
                    HttpStatus.BAD_REQUEST,
                    "INVALID_IMPORT_TEMPLATE");
        }
        return columnIndexes;
    }

    private static int findColumnIndex(Row headerRow, String header) {
        if (headerRow != null) {
            for (Cell cell : headerRow) {
                if (ExcelSheets.normalizeHeader(ExcelSheets.readCell(cell))
                        .equals(ExcelSheets.normalizeHeader(header))) {
                    return cell.getColumnIndex();
                }
            }
        }
        return -1;
    }

    private byte[] write(
            CatalogImportLayout catalogImportLayout,
            List<CatalogImportRow> catalogImportRows,
            Map<Integer, List<String>> errorsByRowNumber,
            List<String> existingNames,
            boolean isTemplate) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            ExcelStyles excelStyles = ExcelSheets.createStyles(workbook);
            writeDataSheet(workbook, excelStyles, catalogImportLayout, catalogImportRows, errorsByRowNumber, isTemplate);
            writeExistingSheet(workbook, excelStyles, catalogImportLayout.existingHeader(), existingNames);
            writeGuideSheet(workbook, excelStyles, catalogImportLayout.guideLines());
            workbook.write(byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    private static void writeDataSheet(
            Workbook workbook,
            ExcelStyles excelStyles,
            CatalogImportLayout catalogImportLayout,
            List<CatalogImportRow> catalogImportRows,
            Map<Integer, List<String>> errorsByRowNumber,
            boolean isTemplate) {
        Sheet sheet = workbook.createSheet(catalogImportLayout.sheetName());
        List<CatalogImportColumn> columns = catalogImportLayout.columns();
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < columns.size(); i++) {
            Cell headerCell = headerRow.createCell(i);
            headerCell.setCellValue(
                    ExcelSheets.headerText(columns.get(i).header(), columns.get(i).required(), excelStyles));
            headerCell.setCellStyle(excelStyles.header());
            sheet.setColumnWidth(i, columns.get(i).width() * 256);
            sheet.setDefaultColumnStyle(i, excelStyles.input());
        }
        if (!isTemplate) {
            Cell errorHeaderCell = headerRow.createCell(columns.size());
            errorHeaderCell.setCellValue(ERROR_HEADER);
            errorHeaderCell.setCellStyle(excelStyles.header());
            sheet.setColumnWidth(columns.size(), ERROR_COLUMN_WIDTH * 256);
        }
        int rowIndex = 1;
        for (CatalogImportRow catalogImportRow : catalogImportRows) {
            Row row = sheet.createRow(rowIndex++);
            writeRow(row, columns.size(), catalogImportRow, isTemplate ? excelStyles.sample() : excelStyles.data());
            if (!isTemplate) {
                Cell errorCell = row.createCell(columns.size());
                errorCell.setCellValue(String.join(". ",
                        errorsByRowNumber.getOrDefault(catalogImportRow.rowNumber(), List.of())));
                errorCell.setCellStyle(excelStyles.data());
            }
        }
        if (isTemplate) {
            CatalogImportRow blankCatalogImportRow = new CatalogImportRow(0, List.of());
            while (rowIndex <= catalogImportRows.size() + MAX_ROWS) {
                writeRow(sheet.createRow(rowIndex++), columns.size(), blankCatalogImportRow, excelStyles.data());
            }
        }
        sheet.createFreezePane(0, 1);
    }

    private static void writeRow(
            Row row,
            int columnCount,
            CatalogImportRow catalogImportRow,
            CellStyle cellStyle) {
        String[] values = new String[columnCount];
        for (int i = 0; i < columnCount; i++) {
            values[i] = catalogImportRow.value(i);
        }
        ExcelSheets.writeCells(row, cellStyle, values);
    }

    private static void writeExistingSheet(
            Workbook workbook, ExcelStyles excelStyles, String header, List<String> existingNames) {
        Sheet sheet = workbook.createSheet(header);
        sheet.setColumnWidth(0, 8 * 256);
        sheet.setColumnWidth(1, 40 * 256);
        ExcelSheets.writeCells(sheet.createRow(0), excelStyles.header(), "STT", header);
        int rowIndex = 1;
        for (String existingName : existingNames) {
            ExcelSheets.writeCells(
                    sheet.createRow(rowIndex), excelStyles.data(), String.valueOf(rowIndex), existingName);
            rowIndex++;
        }
    }

    private static void writeGuideSheet(Workbook workbook, ExcelStyles excelStyles, List<String> guideLines) {
        Sheet sheet = workbook.createSheet(GUIDE_SHEET_NAME);
        sheet.setColumnWidth(0, GUIDE_COLUMN_WIDTH * 256);
        ExcelSheets.writeCells(sheet.createRow(0), excelStyles.header(), "Cách nhập");
        int rowIndex = 1;
        for (String guideLine : guideLines) {
            ExcelSheets.writeCells(sheet.createRow(rowIndex++), excelStyles.data(), guideLine);
        }
    }
}
