package com.cinema.modules.movie.service;

import com.cinema.modules.movie.model.ExportColumn;
import com.cinema.modules.movie.model.ExportSheet;
import com.cinema.modules.movie.util.ExcelSheets;
import com.cinema.modules.movie.util.ExcelStyles;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;

@Component
public class ExportWorkbook {

    public byte[] write(List<ExportSheet> exportSheets) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            ExcelStyles excelStyles = ExcelSheets.createStyles(workbook);
            CellStyle numberCellStyle = workbook.createCellStyle();
            numberCellStyle.cloneStyleFrom(excelStyles.data());
            numberCellStyle.setDataFormat(workbook.createDataFormat().getFormat("0"));
            CellStyle dateCellStyle = workbook.createCellStyle();
            dateCellStyle.cloneStyleFrom(excelStyles.data());
            dateCellStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));
            for (ExportSheet exportSheet : exportSheets) {
                writeSheet(workbook.createSheet(exportSheet.name()), exportSheet, excelStyles, numberCellStyle, dateCellStyle);
            }
            workbook.write(byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    private static void writeSheet(
            Sheet sheet,
            ExportSheet exportSheet,
            ExcelStyles excelStyles,
            CellStyle numberCellStyle,
            CellStyle dateCellStyle) {
        List<ExportColumn> columns = exportSheet.columns();
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < columns.size(); i++) {
            Cell headerCell = headerRow.createCell(i);
            headerCell.setCellValue(ExcelSheets.headerText(columns.get(i).header(), false, excelStyles));
            headerCell.setCellStyle(excelStyles.header());
            sheet.setColumnWidth(i, columns.get(i).width() * 256);
        }
        int rowIndex = 1;
        for (List<Object> values : exportSheet.rows()) {
            Row row = sheet.createRow(rowIndex++);
            for (int i = 0; i < values.size(); i++) {
                Cell cell = row.createCell(i);
                switch (values.get(i)) {
                    case Integer number -> {
                        cell.setCellValue(number);
                        cell.setCellStyle(numberCellStyle);
                    }
                    case LocalDate date -> {
                        cell.setCellValue(date);
                        cell.setCellStyle(dateCellStyle);
                    }
                    case null -> cell.setCellStyle(excelStyles.data());
                    default -> {
                        cell.setCellValue(values.get(i).toString());
                        cell.setCellStyle(excelStyles.data());
                    }
                }
            }
        }
        sheet.createFreezePane(1, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, exportSheet.rows().size(), 0, columns.size() - 1));
    }
}
