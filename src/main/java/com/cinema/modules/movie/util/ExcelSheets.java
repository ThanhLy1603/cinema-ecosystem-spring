package com.cinema.modules.movie.util;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ExcelSheets {

    public static final String SAMPLE_PREFIX = "[Mẫu]";
    private static final String REQUIRED_MARK = "(*)";
    private static final String FONT_NAME = "Times New Roman";
    private static final short FONT_SIZE = 14;
    private static final DateTimeFormatter CELL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ExcelSheets() {
    }

    public static ExcelStyles createStyles(Workbook workbook) {
        applyBaseFont(workbook.getFontAt(0));
        Font headerFont = createFont(workbook);
        headerFont.setBold(true);
        Font requiredFont = createFont(workbook);
        requiredFont.setBold(true);
        requiredFont.setColor(IndexedColors.RED.getIndex());
        Font sampleFont = createFont(workbook);
        sampleFont.setItalic(true);
        sampleFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

        CellStyle headerCellStyle = createBorderedStyle(workbook);
        headerCellStyle.setFont(headerFont);
        headerCellStyle.setAlignment(HorizontalAlignment.CENTER);
        headerCellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerCellStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        short textFormat = workbook.createDataFormat().getFormat("@");
        CellStyle inputCellStyle = workbook.createCellStyle();
        inputCellStyle.setDataFormat(textFormat);
        CellStyle dataCellStyle = createBorderedStyle(workbook);
        dataCellStyle.setDataFormat(textFormat);
        CellStyle sampleCellStyle = createBorderedStyle(workbook);
        sampleCellStyle.setDataFormat(textFormat);
        sampleCellStyle.setFont(sampleFont);
        return new ExcelStyles(
                headerCellStyle, inputCellStyle, dataCellStyle, sampleCellStyle, headerFont, requiredFont);
    }

    public static RichTextString headerText(String label, boolean isRequired, ExcelStyles excelStyles) {
        String header = isRequired ? label + " " + REQUIRED_MARK : label;
        XSSFRichTextString richTextString = new XSSFRichTextString(header);
        richTextString.applyFont(0, header.length(), excelStyles.headerFont());
        if (isRequired) {
            richTextString.applyFont(
                    header.length() - REQUIRED_MARK.length(), header.length(), excelStyles.requiredFont());
        }
        return richTextString;
    }

    public static void writeCells(Row row, CellStyle cellStyle, String... values) {
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            if (!values[i].isEmpty()) {
                cell.setCellValue(values[i]);
            }
            cell.setCellStyle(cellStyle);
        }
    }

    public static String readCell(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType cellType = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        String value = switch (cellType) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().format(CELL_DATE_FORMAT)
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
        return Normalizer.normalize(value, Normalizer.Form.NFC).strip();
    }

    public static String normalizeHeader(String header) {
        return Normalizer.normalize(header, Normalizer.Form.NFC)
                .replace(REQUIRED_MARK, "")
                .replace("*", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    public static String normalizeName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFC).strip().toLowerCase(Locale.ROOT);
    }

    public static boolean isSample(String value) {
        return value.toLowerCase(Locale.ROOT).startsWith(SAMPLE_PREFIX.toLowerCase(Locale.ROOT));
    }

    private static void applyBaseFont(Font font) {
        font.setFontName(FONT_NAME);
        font.setFontHeightInPoints(FONT_SIZE);
    }

    private static Font createFont(Workbook workbook) {
        Font font = workbook.createFont();
        applyBaseFont(font);
        return font;
    }

    private static CellStyle createBorderedStyle(Workbook workbook) {
        CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setBorderTop(BorderStyle.THIN);
        cellStyle.setBorderRight(BorderStyle.THIN);
        cellStyle.setBorderBottom(BorderStyle.THIN);
        cellStyle.setBorderLeft(BorderStyle.THIN);
        cellStyle.setVerticalAlignment(VerticalAlignment.TOP);
        cellStyle.setWrapText(true);
        return cellStyle;
    }
}
