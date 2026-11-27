package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieCastColumn;
import com.cinema.modules.movie.enums.MovieImportColumn;
import com.cinema.modules.movie.model.MovieImportCastRow;
import com.cinema.modules.movie.model.MovieImportContent;
import com.cinema.modules.movie.model.MovieImportSheetRow;
import com.cinema.modules.movie.util.ExcelSheets;
import com.cinema.modules.movie.util.ExcelStyles;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MovieImportWorkbook {

    public static final int MAX_ROWS = 200;
    public static final String CAST_SHEET_NAME = "Đạo diễn và diễn viên";
    private static final int MAX_CAST_ROWS = 2000;
    private static final int TEMPLATE_CAST_ROWS = 600;
    private static final String PERSON_SHEET_NAME = "Nghệ sĩ";
    private static final String MOVIE_LIST_NAME = "DanhSachPhim";
    private static final String PERSON_LIST_NAME = "DanhSachNgheSi";
    private static final String[] ROLE_CHOICES = {"Đạo diễn", "Diễn viên", "Nhà sản xuất"};
    private static final String MOVIE_SHEET_NAME = "Phim";
    private static final String GENRE_SHEET_NAME = "Thể loại";
    private static final String GUIDE_SHEET_NAME = "Hướng dẫn";
    private static final String LEGACY_GENRE_HEADER = "Thể loại";
    private static final String GENRE_LIST_NAME = "DanhSachTheLoai";
    private static final List<MovieImportColumn> GENRE_CHOICE_COLUMNS =
            List.of(MovieImportColumn.GENRE_1, MovieImportColumn.GENRE_2, MovieImportColumn.GENRE_3);
    private static final String ERROR_HEADER = "Lỗi";
    private static final int ERROR_COLUMN_WIDTH = 70;
    private static final int LIST_NUMBER_COLUMN_WIDTH = 8;
    private static final int LIST_NAME_COLUMN_WIDTH = 40;
    private static final int GUIDE_COLUMN_WIDTH = 100;
    private static final String[] RATED_CHOICES =
            Arrays.stream(AgeRating.values()).map(AgeRating::getDescription).toArray(String[]::new);
    private static final String[] STATUS_CHOICES = {"Bản nháp", "Sắp chiếu", "Đang chiếu", "Ngừng chiếu"};
    private static final List<String> GUIDE_LINES = List.of(
            "Mỗi dòng ở sheet Phim là một phim, tối đa " + MAX_ROWS + " phim mỗi lần.",
            "Cột có dấu (*) màu đỏ là bắt buộc.",
            "Dòng có tên phim bắt đầu bằng [Mẫu] chỉ để tham khảo cách nhập, hệ thống không tạo các dòng này.",
            "Đường dẫn để trống thì hệ thống tự tạo từ tên phim, ví dụ Đào, Phở và Piano thành dao-pho-va-piano.",
            "Nhãn độ tuổi và Trạng thái: bấm vào ô rồi chọn trong danh sách xổ xuống.",
            "Ngày nhập theo dạng ngày/tháng/năm, ví dụ 25/12/2026.",
            "Trạng thái để trống thì phim được tạo là Bản nháp, khách chưa nhìn thấy.",
            "Thể loại 1, 2, 3: bấm vào ô rồi chọn trong danh sách xổ xuống, không cần gõ. Ô không dùng thì để trống.",
            "Phim có hơn 3 thể loại: gõ thêm vào cột Thể loại khác, cách nhau bằng dấu chấm phẩy, tối đa 10 thể loại.",
            "Chỉ dùng thể loại có ở sheet Thể loại. Thiếu thì thêm ở mục Phim, tab Thể loại, rồi tải lại tệp mẫu.",
            "Poster, Banner, Trailer có thể để trống rồi tải tệp hàng loạt sau. Nếu điền, đường dẫn bắt đầu bằng https://.",
            "Không điền đường dẫn tệp trong máy tính vào Poster, Banner, Trailer.",
            "Đạo diễn và diễn viên nhập ở sheet riêng, mỗi người một dòng. Cột Tên phim chọn trong danh sách xổ xuống.",
            "Nghệ sĩ: chọn trong danh sách. Gõ tên chưa có thì hệ thống tạo nghệ sĩ mới, bước xem trước sẽ báo rõ.",
            "Tên nhân vật chỉ điền cho vai trò Diễn viên.",
            "Phim trùng đường dẫn hoặc trùng tên với phim đã có sẽ được bỏ qua.");

    public MovieImportContent read(byte[] content) {
        try (InputStream inputStream = new ByteArrayInputStream(content);
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheet(MOVIE_SHEET_NAME) != null
                    ? workbook.getSheet(MOVIE_SHEET_NAME)
                    : workbook.getSheetAt(0);
            return new MovieImportContent(readRows(sheet), readCastRows(workbook.getSheet(CAST_SHEET_NAME)));
        } catch (AppException appException) {
            throw appException;
        } catch (IOException | RuntimeException exception) {
            throw new AppException(
                    "Không đọc được tệp Excel, hãy dùng tệp mẫu định dạng .xlsx",
                    HttpStatus.BAD_REQUEST,
                    "INVALID_IMPORT_FILE");
        }
    }

    public byte[] writeTemplate(List<String> genreNames, List<String> personNames) {
        return write(
                new MovieImportContent(buildSampleRows(genreNames), buildSampleCastRows()),
                Map.of(),
                genreNames,
                personNames,
                true);
    }

    public byte[] writeRejectedRows(
            MovieImportContent movieImportContent,
            Map<Integer, List<String>> errorsByRowNumber,
            List<String> genreNames,
            List<String> personNames) {
        return write(movieImportContent, errorsByRowNumber, genreNames, personNames, false);
    }

    private List<MovieImportCastRow> readCastRows(Sheet sheet) {
        if (sheet == null || sheet.getRow(sheet.getFirstRowNum()) == null) {
            return List.of();
        }
        Map<Integer, MovieCastColumn> columnsByIndex = readCastHeader(sheet.getRow(sheet.getFirstRowNum()));
        List<MovieImportCastRow> movieImportCastRows = new ArrayList<>();
        for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Map<MovieCastColumn, String> values = new EnumMap<>(MovieCastColumn.class);
            columnsByIndex.forEach((columnIndex, movieCastColumn) -> {
                String value = ExcelSheets.readCell(row.getCell(columnIndex));
                if (!value.isEmpty()) {
                    values.put(movieCastColumn, value);
                }
            });
            if (values.isEmpty() || ExcelSheets.isSample(values.getOrDefault(MovieCastColumn.MOVIE_TITLE, ""))) {
                continue;
            }
            if (movieImportCastRows.size() == MAX_CAST_ROWS) {
                throw new AppException(
                        "Sheet " + CAST_SHEET_NAME + " tối đa " + MAX_CAST_ROWS + " dòng",
                        HttpStatus.BAD_REQUEST,
                        "TOO_MANY_IMPORT_ROWS");
            }
            movieImportCastRows.add(new MovieImportCastRow(rowIndex + 1, values));
        }
        return movieImportCastRows;
    }

    private Map<Integer, MovieCastColumn> readCastHeader(Row headerRow) {
        Map<Integer, MovieCastColumn> columnsByIndex = new HashMap<>();
        for (Cell cell : headerRow) {
            String header = ExcelSheets.normalizeHeader(ExcelSheets.readCell(cell));
            for (MovieCastColumn movieCastColumn : MovieCastColumn.values()) {
                if (ExcelSheets.normalizeHeader(movieCastColumn.getHeader()).equals(header)) {
                    columnsByIndex.put(cell.getColumnIndex(), movieCastColumn);
                }
            }
        }
        List<String> missingHeaders = Arrays.stream(MovieCastColumn.values())
                .filter(movieCastColumn -> movieCastColumn.isRequired()
                        && !columnsByIndex.containsValue(movieCastColumn))
                .map(MovieCastColumn::getHeader)
                .toList();
        if (!missingHeaders.isEmpty()) {
            throw new AppException(
                    "Sheet " + CAST_SHEET_NAME + " thiếu cột: " + String.join(", ", missingHeaders),
                    HttpStatus.BAD_REQUEST,
                    "INVALID_IMPORT_TEMPLATE");
        }
        return columnsByIndex;
    }

    private List<MovieImportSheetRow> readRows(Sheet sheet) {
        Map<Integer, MovieImportColumn> columnsByIndex = readHeader(sheet.getRow(sheet.getFirstRowNum()));
        List<MovieImportSheetRow> movieImportSheetRows = new ArrayList<>();
        for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Map<MovieImportColumn, String> values = new EnumMap<>(MovieImportColumn.class);
            columnsByIndex.forEach((columnIndex, movieImportColumn) -> {
                String value = ExcelSheets.readCell(row.getCell(columnIndex));
                if (!value.isEmpty()) {
                    values.put(movieImportColumn, value);
                }
            });
            if (values.isEmpty() || ExcelSheets.isSample(values.getOrDefault(MovieImportColumn.TITLE, ""))) {
                continue;
            }
            if (movieImportSheetRows.size() == MAX_ROWS) {
                throw new AppException(
                        "Mỗi lần nhập tối đa " + MAX_ROWS + " phim, hãy tách tệp thành nhiều phần",
                        HttpStatus.BAD_REQUEST,
                        "TOO_MANY_IMPORT_ROWS");
            }
            movieImportSheetRows.add(new MovieImportSheetRow(rowIndex + 1, values));
        }
        if (movieImportSheetRows.isEmpty()) {
            throw new AppException(
                    "Tệp chưa có phim nào. Các dòng bắt đầu bằng [Mẫu] không được nhập",
                    HttpStatus.BAD_REQUEST,
                    "EMPTY_IMPORT_FILE");
        }
        return movieImportSheetRows;
    }

    private Map<Integer, MovieImportColumn> readHeader(Row headerRow) {
        Map<Integer, MovieImportColumn> columnsByIndex = new HashMap<>();
        if (headerRow != null) {
            for (Cell cell : headerRow) {
                String header = ExcelSheets.normalizeHeader(ExcelSheets.readCell(cell));
                if (header.equals(ExcelSheets.normalizeHeader(LEGACY_GENRE_HEADER))) {
                    columnsByIndex.put(cell.getColumnIndex(), MovieImportColumn.OTHER_GENRES);
                }
                for (MovieImportColumn movieImportColumn : MovieImportColumn.values()) {
                    if (ExcelSheets.normalizeHeader(movieImportColumn.getHeader()).equals(header)) {
                        columnsByIndex.put(cell.getColumnIndex(), movieImportColumn);
                    }
                }
            }
        }
        List<String> missingHeaders = Arrays.stream(MovieImportColumn.values())
                .filter(movieImportColumn -> movieImportColumn.isRequired()
                        && !columnsByIndex.containsValue(movieImportColumn))
                .map(MovieImportColumn::getHeader)
                .toList();
        if (!missingHeaders.isEmpty()) {
            throw new AppException(
                    "Tệp thiếu cột: " + String.join(", ", missingHeaders) + ". Hãy dùng tệp mẫu",
                    HttpStatus.BAD_REQUEST,
                    "INVALID_IMPORT_TEMPLATE");
        }
        return columnsByIndex;
    }

    private static List<MovieImportCastRow> buildSampleCastRows() {
        String movieTitle = ExcelSheets.SAMPLE_PREFIX + " Mai";
        return List.of(
                new MovieImportCastRow(2, Map.of(
                        MovieCastColumn.MOVIE_TITLE, movieTitle,
                        MovieCastColumn.PERSON_NAME, "Trấn Thành",
                        MovieCastColumn.ROLE, "Đạo diễn")),
                new MovieImportCastRow(3, Map.of(
                        MovieCastColumn.MOVIE_TITLE, movieTitle,
                        MovieCastColumn.PERSON_NAME, "Phương Anh Đào",
                        MovieCastColumn.ROLE, "Diễn viên",
                        MovieCastColumn.CHARACTER_NAME, "Mai")));
    }

    private static List<MovieImportSheetRow> buildSampleRows(List<String> genreNames) {
        String firstGenre = genreNames.isEmpty() ? "" : genreNames.getFirst();
        String secondGenre = genreNames.size() < 2 ? "" : genreNames.get(1);
        return List.of(
                sampleRow(2, Map.of(
                        MovieImportColumn.TITLE, ExcelSheets.SAMPLE_PREFIX + " Mai",
                        MovieImportColumn.ORIGINAL_TITLE, "Mai",
                        MovieImportColumn.DURATION, "131",
                        MovieImportColumn.RELEASE_DATE, "10/02/2027",
                        MovieImportColumn.END_DATE, "30/03/2027",
                        MovieImportColumn.RATED, AgeRating.C18.getDescription(),
                        MovieImportColumn.STATUS, "Sắp chiếu",
                        MovieImportColumn.GENRE_1, firstGenre,
                        MovieImportColumn.GENRE_2, secondGenre,
                        MovieImportColumn.DESCRIPTION, "Chuyện đời của một người phụ nữ tên Mai.")),
                sampleRow(3, Map.of(
                        MovieImportColumn.TITLE, ExcelSheets.SAMPLE_PREFIX + " Đào, Phở và Piano",
                        MovieImportColumn.SLUG, "dao-pho-va-piano",
                        MovieImportColumn.DURATION, "100",
                        MovieImportColumn.RELEASE_DATE, "22/12/2026",
                        MovieImportColumn.RATED, AgeRating.C13.getDescription(),
                        MovieImportColumn.STATUS, "Bản nháp",
                        MovieImportColumn.GENRE_1, firstGenre,
                        MovieImportColumn.POSTER, "https://example.com/poster.jpg",
                        MovieImportColumn.TRAILER, "https://example.com/trailer.mp4")));
    }

    private static MovieImportSheetRow sampleRow(int rowNumber, Map<MovieImportColumn, String> values) {
        return new MovieImportSheetRow(rowNumber, values);
    }

    private byte[] write(
            MovieImportContent movieImportContent,
            Map<Integer, List<String>> errorsByRowNumber,
            List<String> genreNames,
            List<String> personNames,
            boolean isTemplate) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            ExcelStyles excelStyles = ExcelSheets.createStyles(workbook);
            List<MovieImportSheetRow> movieImportSheetRows = movieImportContent.movieRows();
            Sheet movieSheet =
                    writeMovieSheet(workbook, excelStyles, movieImportSheetRows, errorsByRowNumber, isTemplate);
            Sheet castSheet = writeCastSheet(workbook, excelStyles, movieImportContent.castRows(), isTemplate);
            writeListSheet(workbook, excelStyles, GENRE_SHEET_NAME, "Thể loại đang có", GENRE_LIST_NAME, genreNames);
            writeListSheet(workbook, excelStyles, PERSON_SHEET_NAME, "Nghệ sĩ đang có", PERSON_LIST_NAME, personNames);
            writeGuideSheet(workbook, excelStyles);
            int lastMovieRowIndex = movieImportSheetRows.size() + MAX_ROWS;
            addGenreChoices(movieSheet, genreNames, lastMovieRowIndex);
            defineName(workbook, MOVIE_LIST_NAME, MOVIE_SHEET_NAME, "A", lastMovieRowIndex + 1);
            addCastChoices(castSheet, personNames, movieImportContent.castRows().size() + TEMPLATE_CAST_ROWS);
            workbook.write(byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    private static Sheet writeCastSheet(
            Workbook workbook,
            ExcelStyles excelStyles,
            List<MovieImportCastRow> movieImportCastRows,
            boolean isTemplate) {
        Sheet sheet = workbook.createSheet(CAST_SHEET_NAME);
        Row headerRow = sheet.createRow(0);
        for (MovieCastColumn movieCastColumn : MovieCastColumn.values()) {
            int columnIndex = movieCastColumn.ordinal();
            Cell headerCell = headerRow.createCell(columnIndex);
            headerCell.setCellValue(
                    ExcelSheets.headerText(movieCastColumn.getHeader(), movieCastColumn.isRequired(), excelStyles));
            headerCell.setCellStyle(excelStyles.header());
            sheet.setColumnWidth(columnIndex, movieCastColumn.getWidth() * 256);
            sheet.setDefaultColumnStyle(columnIndex, excelStyles.input());
        }
        int rowIndex = 1;
        for (MovieImportCastRow movieImportCastRow : movieImportCastRows) {
            writeCastRow(
                    sheet.createRow(rowIndex++),
                    movieImportCastRow,
                    isTemplate ? excelStyles.sample() : excelStyles.data());
        }
        if (isTemplate) {
            MovieImportCastRow blankMovieImportCastRow = new MovieImportCastRow(0, Map.of());
            while (rowIndex <= movieImportCastRows.size() + TEMPLATE_CAST_ROWS) {
                writeCastRow(sheet.createRow(rowIndex++), blankMovieImportCastRow, excelStyles.data());
            }
        }
        sheet.createFreezePane(0, 1);
        return sheet;
    }

    private static void writeCastRow(Row row, MovieImportCastRow movieImportCastRow, CellStyle cellStyle) {
        for (MovieCastColumn movieCastColumn : MovieCastColumn.values()) {
            Cell cell = row.createCell(movieCastColumn.ordinal());
            String value = movieImportCastRow.value(movieCastColumn);
            if (!value.isEmpty()) {
                cell.setCellValue(value);
            }
            cell.setCellStyle(cellStyle);
        }
    }

    private static void addCastChoices(Sheet sheet, List<String> personNames, int lastRowIndex) {
        DataValidationHelper dataValidationHelper = sheet.getDataValidationHelper();
        DataValidation movieDataValidation = dataValidationHelper.createValidation(
                dataValidationHelper.createFormulaListConstraint(MOVIE_LIST_NAME),
                toCastColumnRange(MovieCastColumn.MOVIE_TITLE, lastRowIndex));
        movieDataValidation.createPromptBox("Chọn phim", "Chọn tên phim đã nhập ở sheet Phim.");
        movieDataValidation.setShowPromptBox(true);
        movieDataValidation.createErrorBox("Sai tên phim", "Hãy chọn phim có ở sheet Phim.");
        movieDataValidation.setShowErrorBox(true);
        sheet.addValidationData(movieDataValidation);
        if (!personNames.isEmpty()) {
            DataValidation personDataValidation = dataValidationHelper.createValidation(
                    dataValidationHelper.createFormulaListConstraint(PERSON_LIST_NAME),
                    toCastColumnRange(MovieCastColumn.PERSON_NAME, lastRowIndex));
            personDataValidation.createPromptBox("Chọn nghệ sĩ", "Chọn trong danh sách, hoặc gõ tên nghệ sĩ mới.");
            personDataValidation.setShowPromptBox(true);
            personDataValidation.setShowErrorBox(false);
            sheet.addValidationData(personDataValidation);
        }
        DataValidation roleDataValidation = dataValidationHelper.createValidation(
                dataValidationHelper.createExplicitListConstraint(ROLE_CHOICES),
                toCastColumnRange(MovieCastColumn.ROLE, lastRowIndex));
        roleDataValidation.setShowErrorBox(true);
        sheet.addValidationData(roleDataValidation);
    }

    private static CellRangeAddressList toCastColumnRange(MovieCastColumn movieCastColumn, int lastRowIndex) {
        int columnIndex = movieCastColumn.ordinal();
        return new CellRangeAddressList(1, lastRowIndex, columnIndex, columnIndex);
    }

    private static void defineName(Workbook workbook, String name, String sheetName, String column, int lastRow) {
        Name definedName = workbook.createName();
        definedName.setNameName(name);
        definedName.setRefersToFormula("'" + sheetName + "'!$" + column + "$2:$" + column + "$" + lastRow);
    }

    private Sheet writeMovieSheet(
            XSSFWorkbook workbook,
            ExcelStyles excelStyles,
            List<MovieImportSheetRow> movieImportSheetRows,
            Map<Integer, List<String>> errorsByRowNumber,
            boolean isTemplate) {
        Sheet sheet = workbook.createSheet(MOVIE_SHEET_NAME);
        writeHeaderRow(sheet, excelStyles, !isTemplate);
        int errorColumnIndex = MovieImportColumn.values().length;
        int rowIndex = 1;
        for (MovieImportSheetRow movieImportSheetRow : movieImportSheetRows) {
            Row row = sheet.createRow(rowIndex++);
            writeDataRow(row, movieImportSheetRow, isTemplate ? excelStyles.sample() : excelStyles.data());
            if (!isTemplate) {
                Cell errorCell = row.createCell(errorColumnIndex);
                errorCell.setCellValue(String.join(". ",
                        errorsByRowNumber.getOrDefault(movieImportSheetRow.rowNumber(), List.of())));
                errorCell.setCellStyle(excelStyles.data());
            }
        }
        int lastInputRowIndex = movieImportSheetRows.size() + MAX_ROWS;
        if (isTemplate) {
            MovieImportSheetRow blankMovieImportSheetRow = new MovieImportSheetRow(0, Map.of());
            while (rowIndex <= lastInputRowIndex) {
                writeDataRow(sheet.createRow(rowIndex++), blankMovieImportSheetRow, excelStyles.data());
            }
        }
        addChoices(sheet, MovieImportColumn.RATED, RATED_CHOICES, lastInputRowIndex);
        addChoices(sheet, MovieImportColumn.STATUS, STATUS_CHOICES, lastInputRowIndex);
        sheet.createFreezePane(1, 1);
        return sheet;
    }

    private static void writeHeaderRow(Sheet sheet, ExcelStyles excelStyles, boolean withErrorColumn) {
        Row headerRow = sheet.createRow(0);
        for (MovieImportColumn movieImportColumn : MovieImportColumn.values()) {
            int columnIndex = movieImportColumn.ordinal();
            Cell headerCell = headerRow.createCell(columnIndex);
            headerCell.setCellValue(ExcelSheets.headerText(
                    movieImportColumn.getHeader(), movieImportColumn.isRequired(), excelStyles));
            headerCell.setCellStyle(excelStyles.header());
            sheet.setColumnWidth(columnIndex, movieImportColumn.getWidth() * 256);
            sheet.setDefaultColumnStyle(columnIndex, excelStyles.input());
        }
        if (withErrorColumn) {
            int errorColumnIndex = MovieImportColumn.values().length;
            Cell errorHeaderCell = headerRow.createCell(errorColumnIndex);
            errorHeaderCell.setCellValue(ERROR_HEADER);
            errorHeaderCell.setCellStyle(excelStyles.header());
            sheet.setColumnWidth(errorColumnIndex, ERROR_COLUMN_WIDTH * 256);
        }
    }

    private static void writeDataRow(Row row, MovieImportSheetRow movieImportSheetRow, CellStyle cellStyle) {
        for (MovieImportColumn movieImportColumn : MovieImportColumn.values()) {
            Cell cell = row.createCell(movieImportColumn.ordinal());
            String value = movieImportSheetRow.value(movieImportColumn);
            if (!value.isEmpty()) {
                cell.setCellValue(value);
            }
            cell.setCellStyle(cellStyle);
        }
    }

    private static void addChoices(
            Sheet sheet, MovieImportColumn movieImportColumn, String[] choices, int lastRowIndex) {
        DataValidationHelper dataValidationHelper = sheet.getDataValidationHelper();
        DataValidation dataValidation = dataValidationHelper.createValidation(
                dataValidationHelper.createExplicitListConstraint(choices),
                toColumnRange(movieImportColumn, lastRowIndex));
        dataValidation.setShowErrorBox(true);
        sheet.addValidationData(dataValidation);
    }

    private static void writeListSheet(
            Workbook workbook,
            ExcelStyles excelStyles,
            String sheetName,
            String header,
            String listName,
            List<String> names) {
        Sheet sheet = workbook.createSheet(sheetName);
        sheet.setColumnWidth(0, LIST_NUMBER_COLUMN_WIDTH * 256);
        sheet.setColumnWidth(1, LIST_NAME_COLUMN_WIDTH * 256);
        ExcelSheets.writeCells(sheet.createRow(0), excelStyles.header(), "STT", header);
        int rowIndex = 1;
        for (String name : names) {
            ExcelSheets.writeCells(sheet.createRow(rowIndex), excelStyles.data(), String.valueOf(rowIndex), name);
            rowIndex++;
        }
        if (!names.isEmpty()) {
            defineName(workbook, listName, sheetName, "B", names.size() + 1);
        }
    }

    private static void writeGuideSheet(Workbook workbook, ExcelStyles excelStyles) {
        Sheet sheet = workbook.createSheet(GUIDE_SHEET_NAME);
        sheet.setColumnWidth(0, GUIDE_COLUMN_WIDTH * 256);
        ExcelSheets.writeCells(sheet.createRow(0), excelStyles.header(), "Cách nhập");
        int rowIndex = 1;
        for (String guideLine : GUIDE_LINES) {
            ExcelSheets.writeCells(sheet.createRow(rowIndex++), excelStyles.data(), guideLine);
        }
    }

    private static void addGenreChoices(Sheet sheet, List<String> genreNames, int lastRowIndex) {
        DataValidationHelper dataValidationHelper = sheet.getDataValidationHelper();
        if (!genreNames.isEmpty()) {
            for (MovieImportColumn movieImportColumn : GENRE_CHOICE_COLUMNS) {
                DataValidation dataValidation = dataValidationHelper.createValidation(
                        dataValidationHelper.createFormulaListConstraint(GENRE_LIST_NAME),
                        toColumnRange(movieImportColumn, lastRowIndex));
                dataValidation.createPromptBox("Chọn thể loại", "Bấm mũi tên rồi chọn trong danh sách.");
                dataValidation.setShowPromptBox(true);
                dataValidation.createErrorBox("Sai thể loại", "Hãy chọn thể loại có trong danh sách.");
                dataValidation.setShowErrorBox(true);
                sheet.addValidationData(dataValidation);
            }
        }
        DataValidation otherGenresDataValidation = dataValidationHelper.createValidation(
                dataValidationHelper.createTextLengthConstraint(
                        DataValidationConstraint.OperatorType.GREATER_OR_EQUAL, "0", null),
                toColumnRange(MovieImportColumn.OTHER_GENRES, lastRowIndex));
        otherGenresDataValidation.createPromptBox(
                "Khi phim có hơn 3 thể loại",
                "Gõ đúng tên ở sheet Thể loại, cách nhau bằng dấu chấm phẩy. Ví dụ: Hài; Gia Đình");
        otherGenresDataValidation.setShowPromptBox(true);
        sheet.addValidationData(otherGenresDataValidation);
    }

    private static CellRangeAddressList toColumnRange(MovieImportColumn movieImportColumn, int lastRowIndex) {
        int columnIndex = movieImportColumn.ordinal();
        return new CellRangeAddressList(1, lastRowIndex, columnIndex, columnIndex);
    }
}
