package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.movie.dto.request.MovieCastRequest;
import com.cinema.modules.movie.dto.request.MovieRequest;
import com.cinema.modules.movie.dto.request.PersonRequest;
import com.cinema.modules.movie.dto.response.MovieImportReportResponse;
import com.cinema.modules.movie.dto.response.MovieImportRowResponse;
import com.cinema.modules.movie.dto.response.PersonResponse;
import com.cinema.modules.movie.entity.Genre;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.entity.Person;
import com.cinema.modules.movie.enums.CastRoleType;
import com.cinema.modules.movie.enums.MovieImportColumn;
import com.cinema.modules.movie.enums.ImportRowStatus;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.model.MovieImportSheetRow;
import com.cinema.modules.movie.repository.GenreRepository;
import com.cinema.modules.movie.repository.MovieRepository;
import com.cinema.modules.movie.service.interfaces.IMovieAdminService;
import com.cinema.modules.movie.repository.PersonRepository;
import com.cinema.modules.movie.service.interfaces.IPersonAdminService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MovieImportServiceTest {

    private static final List<String> HEADERS = List.of(
            "Tên phim (*)", "Đường dẫn", "Tên gốc", "Thời lượng phút (*)", "Ngày khởi chiếu (*)", "Ngày kết thúc",
            "Nhãn độ tuổi (*)", "Trạng thái", "Thể loại 1", "Thể loại 2",
            "Thể loại 3", "Thể loại khác");

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private IMovieAdminService movieAdminService;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private IPersonAdminService personAdminService;

    private final MovieImportWorkbook movieImportWorkbook = new MovieImportWorkbook();
    private MovieImportService movieImportService;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        movieImportService = new MovieImportService(
                movieImportWorkbook,
                new MovieImportRowParser(validator),
                new MovieSlugResolver(movieRepository),
                movieRepository,
                genreRepository,
                movieAdminService,
                new MovieImportCastResolver(),
                personRepository,
                personAdminService);
        Person person = new Person();
        person.setId(11L);
        person.setName("Trấn Thành");
        when(personRepository.findAll(any(Sort.class))).thenReturn(List.of(person));
        Genre genre = new Genre();
        genre.setId(3L);
        genre.setName("Hành động");
        when(genreRepository.findAllByOrderByNameAsc()).thenReturn(List.of(genre));
    }

    private static byte[] workbook(List<String> headers, List<List<String>> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Phim");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }
            for (int i = 0; i < rows.size(); i++) {
                Row row = sheet.createRow(i + 1);
                for (int j = 0; j < rows.get(i).size(); j++) {
                    row.createCell(j).setCellValue(rows.get(i).get(j));
                }
            }
            workbook.write(byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        }
    }

    private static List<String> validRow(String title) {
        return List.of(title, "", "", "120", "25/12/2026", "", "c13", "", "hành động");
    }

    @Test
    void previewAcceptsValidRowsAndDefaultsToDraft() throws IOException {
        MovieImportReportResponse movieImportReportResponse =
                movieImportService.previewImport(workbook(HEADERS, List.of(validRow("Đào, Phở và Piano"))));

        MovieImportRowResponse movieImportRowResponse = movieImportReportResponse.rows().getFirst();
        assertEquals(1, movieImportReportResponse.validRows());
        assertEquals(2, movieImportRowResponse.rowNumber());
        assertEquals("dao-pho-va-piano", movieImportRowResponse.slug());
        assertEquals(MovieStatus.DRAFT, movieImportRowResponse.status());
        assertEquals(AgeRating.C13, movieImportRowResponse.rated());
        assertEquals(LocalDate.of(2026, 12, 25), movieImportRowResponse.releaseDate());
        assertEquals(List.of("Hành động"), movieImportRowResponse.genreNames());
        verify(movieAdminService, never()).createMovie(any());
    }

    @Test
    void previewReportsEveryProblemOfARow() throws IOException {
        MovieImportReportResponse movieImportReportResponse = movieImportService.previewImport(workbook(
                HEADERS,
                List.of(List.of("", "Sai Slug", "", "abc", "31/02/2026", "", "C20", "Đang bán", "Kinh dị"))));

        List<String> errors = movieImportReportResponse.rows().getFirst().errors();
        assertEquals(1, movieImportReportResponse.errorRows());
        assertEquals(7, errors.size());
        assertTrue(errors.contains("Vui lòng nhập tên phim"));
        assertTrue(errors.contains("Thời lượng phải là số phút nguyên"));
        assertTrue(errors.contains("Chưa có thể loại: Kinh dị. Hãy chọn trong danh sách ở sheet Thể loại"));
    }

    @Test
    void previewRejectsEndDateBeforeReleaseDate() throws IOException {
        MovieImportReportResponse movieImportReportResponse = movieImportService.previewImport(workbook(
                HEADERS, List.of(List.of("Mai", "", "", "100", "10/01/2027", "01/01/2027", "P"))));

        assertEquals(
                List.of("Ngày kết thúc chiếu phải sau hoặc bằng ngày khởi chiếu"),
                movieImportReportResponse.rows().getFirst().errors());
    }

    @Test
    void duplicatesInsideTheFileAndInTheCatalogAreSkipped() throws IOException {
        when(movieRepository.existsByTitleIgnoreCaseAndDeletedFalse("Lật Mặt")).thenReturn(true);

        MovieImportReportResponse movieImportReportResponse = movieImportService.importMovies(
                workbook(HEADERS, List.of(validRow("Mai"), validRow("MAI"), validRow("Lật Mặt"))));

        assertEquals(1, movieImportReportResponse.createdRows());
        assertEquals(2, movieImportReportResponse.duplicateRows());
        assertEquals(List.of("Trùng phim với dòng 2"), movieImportReportResponse.rows().get(1).errors());
        ArgumentCaptor<MovieRequest> movieRequestCaptor = ArgumentCaptor.forClass(MovieRequest.class);
        verify(movieAdminService).createMovie(movieRequestCaptor.capture());
        assertEquals("mai", movieRequestCaptor.getValue().slug());
        assertEquals(List.of(3L), movieRequestCaptor.getValue().genreIds());
    }

    @Test
    void failureOfOneRowDoesNotStopTheOthers() throws IOException {
        when(movieAdminService.createMovie(any()))
                .thenThrow(new AppException("Đường dẫn vừa bị dùng", HttpStatus.CONFLICT, "MOVIE_SLUG_EXISTS"))
                .thenReturn(null);

        MovieImportReportResponse movieImportReportResponse =
                movieImportService.importMovies(workbook(HEADERS, List.of(validRow("Mai"), validRow("Tết"))));

        assertEquals(ImportRowStatus.ERROR, movieImportReportResponse.rows().get(0).rowStatus());
        assertEquals(ImportRowStatus.CREATED, movieImportReportResponse.rows().get(1).rowStatus());
    }

    @Test
    void exportedRowsCanBeReadBackWithTheirOriginalValues() throws IOException {
        byte[] content = workbook(HEADERS, List.of(validRow("Mai"), validRow("Tết")));

        List<MovieImportSheetRow> movieImportSheetRows =
                movieImportWorkbook.read(movieImportService.exportRows(content, List.of(3))).movieRows();

        assertEquals(1, movieImportSheetRows.size());
        assertEquals("Tết", movieImportSheetRows.getFirst().value(MovieImportColumn.TITLE));
        assertEquals("25/12/2026", movieImportSheetRows.getFirst().value(MovieImportColumn.RELEASE_DATE));
    }

    @Test
    void templateUsesTimesNewRomanBordersRedMarksAndSampleRows() throws IOException {
        try (XSSFWorkbook workbook =
                new XSSFWorkbook(new ByteArrayInputStream(movieImportService.buildTemplate()))) {
            Sheet sheet = workbook.getSheet("Phim");
            for (Row row : sheet) {
                for (Cell cell : row) {
                    XSSFFont font = ((XSSFCellStyle) cell.getCellStyle()).getFont();
                    assertEquals("Times New Roman", font.getFontName());
                    assertEquals(14, font.getFontHeightInPoints());
                    assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderBottom());
                    assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderRight());
                }
            }
            XSSFRichTextString titleHeader = (XSSFRichTextString) sheet.getRow(0).getCell(0).getRichStringCellValue();
            assertEquals("Tên phim (*)", titleHeader.getString());
            XSSFFont markFont = titleHeader.getFontAtIndex(titleHeader.length() - 1);
            assertEquals(IndexedColors.RED.getIndex(), markFont.getColor());
            assertEquals("Times New Roman", markFont.getFontName());
            assertTrue(titleHeader.getFontAtIndex(0).getColor() != IndexedColors.RED.getIndex());
            assertEquals("Đường dẫn", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("[Mẫu] Mai", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Hành động", sheet.getRow(1).getCell(MovieImportColumn.GENRE_1.ordinal()).getStringCellValue());
        }
    }

    @Test
    void templateOffersExistingGenresAsDropdowns() throws IOException {
        try (XSSFWorkbook workbook =
                new XSSFWorkbook(new ByteArrayInputStream(movieImportService.buildTemplate()))) {
            assertEquals("Hành động", workbook.getSheet("Thể loại").getRow(1).getCell(1).getStringCellValue());
            assertEquals("'Thể loại'!$B$2:$B$2", workbook.getName("DanhSachTheLoai").getRefersToFormula());
            List<Integer> dropdownColumns = workbook.getSheet("Phim").getDataValidations().stream()
                    .filter(dataValidation ->
                            "DanhSachTheLoai".equals(dataValidation.getValidationConstraint().getFormula1()))
                    .map(dataValidation -> dataValidation.getRegions().getCellRangeAddress(0).getFirstColumn())
                    .sorted()
                    .toList();
            assertEquals(
                    List.of(
                            MovieImportColumn.GENRE_1.ordinal(),
                            MovieImportColumn.GENRE_2.ordinal(),
                            MovieImportColumn.GENRE_3.ordinal()),
                    dropdownColumns);
        }
    }

    @Test
    void genresFromEveryGenreColumnAreMerged() throws IOException {
        Genre comedyGenre = new Genre();
        comedyGenre.setId(4L);
        comedyGenre.setName("Hài");
        Genre actionGenre = new Genre();
        actionGenre.setId(3L);
        actionGenre.setName("Hành động");
        when(genreRepository.findAllByOrderByNameAsc()).thenReturn(List.of(comedyGenre, actionGenre));

        MovieImportReportResponse movieImportReportResponse = movieImportService.previewImport(workbook(
                HEADERS,
                List.of(List.of("Mai", "", "", "100", "10/01/2027", "", "P", "", "Hài", "", "Hài", "hành động"))));

        assertEquals(List.of("Hài", "Hành động"), movieImportReportResponse.rows().getFirst().genreNames());
    }

    @Test
    void genreColumnOfTheEarlierTemplateIsStillRead() throws IOException {
        List<String> earlierHeaders = List.of(
                "Tên phim", "Thời lượng phút", "Ngày khởi chiếu", "Nhãn độ tuổi", "Thể loại");

        MovieImportReportResponse movieImportReportResponse = movieImportService.previewImport(
                workbook(earlierHeaders, List.of(List.of("Mai", "100", "10/01/2027", "P", "Hành động"))));

        assertEquals(List.of("Hành động"), movieImportReportResponse.rows().getFirst().genreNames());
    }

    @Test
    void ageRatingIsChosenByItsFullDescription() throws IOException {
        MovieImportReportResponse movieImportReportResponse = movieImportService.previewImport(workbook(
                HEADERS,
                List.of(List.of("Mai", "", "", "100", "10/01/2027", "", "Cấm khán giả dưới 16 tuổi"))));

        assertEquals(AgeRating.C16, movieImportReportResponse.rows().getFirst().rated());
        try (XSSFWorkbook workbook =
                new XSSFWorkbook(new ByteArrayInputStream(movieImportService.buildTemplate()))) {
            Sheet sheet = workbook.getSheet("Phim");
            assertEquals(
                    "Cấm khán giả dưới 18 tuổi",
                    sheet.getRow(1).getCell(MovieImportColumn.RATED.ordinal()).getStringCellValue());
            assertTrue(sheet.getDataValidations().stream().anyMatch(dataValidation -> List.of(
                            "Phổ biến mọi lứa tuổi",
                            "Cấm khán giả dưới 13 tuổi",
                            "Cấm khán giả dưới 16 tuổi",
                            "Cấm khán giả dưới 18 tuổi")
                    .equals(dataValidation.getValidationConstraint().getExplicitListValues() == null
                            ? List.of()
                            : List.of(dataValidation.getValidationConstraint().getExplicitListValues()))));
        }
    }

    private static byte[] workbookWithCasts(List<List<String>> movieRows, List<List<String>> castRows)
            throws IOException {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbook(HEADERS, movieRows)));
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Đạo diễn và diễn viên");
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("Tên phim (*)", "Nghệ sĩ (*)", "Vai trò (*)", "Tên nhân vật"));
            rows.addAll(castRows);
            for (int i = 0; i < rows.size(); i++) {
                Row row = sheet.createRow(i);
                for (int j = 0; j < rows.get(i).size(); j++) {
                    row.createCell(j).setCellValue(rows.get(i).get(j));
                }
            }
            workbook.write(byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        }
    }

    @Test
    void castsAreImportedAndMissingArtistsAreCreatedOnce() throws IOException {
        when(personAdminService.createPerson(any()))
                .thenReturn(new PersonResponse(12L, "Phương Anh Đào", null, null));
        byte[] content = workbookWithCasts(
                List.of(validRow("Mai"), validRow("Tết")),
                List.of(
                        List.of("Mai", "trấn thành", "Đạo diễn"),
                        List.of("Mai", "Phương Anh Đào", "Diễn viên", "Mai"),
                        List.of("Tết", "Phương Anh Đào", "diễn viên"),
                        List.of("[Mẫu] Mai", "Không nhập", "Đạo diễn")));

        MovieImportReportResponse previewReport = movieImportService.previewImport(content);

        assertEquals(2, previewReport.rows().getFirst().castCount());
        assertEquals(List.of("Phương Anh Đào"), previewReport.rows().getFirst().newPersonNames());
        verify(personAdminService, never()).createPerson(any());

        assertEquals(2, movieImportService.importMovies(content).createdRows());
        verify(personAdminService).createPerson(new PersonRequest("Phương Anh Đào", null, null));
        ArgumentCaptor<MovieRequest> movieRequestCaptor = ArgumentCaptor.forClass(MovieRequest.class);
        verify(movieAdminService, times(2)).createMovie(movieRequestCaptor.capture());
        assertEquals(
                List.of(
                        new MovieCastRequest(11L, CastRoleType.DIRECTOR, null),
                        new MovieCastRequest(12L, CastRoleType.ACTOR, "Mai")),
                movieRequestCaptor.getAllValues().getFirst().casts());
    }

    @Test
    void wrongCastRowsBlockTheirMovieAndUnknownTitlesAreReported() throws IOException {
        byte[] content = workbookWithCasts(
                List.of(validRow("Mai"), validRow("Tết")),
                List.of(
                        List.of("Mai", "Trấn Thành", "Quay phim"),
                        List.of("Mai", "Trấn Thành", "Đạo diễn", "Ông Tư"),
                        List.of("Phim lạ", "Trấn Thành", "Đạo diễn")));

        MovieImportReportResponse movieImportReportResponse = movieImportService.importMovies(content);

        assertEquals(ImportRowStatus.ERROR, movieImportReportResponse.rows().get(0).rowStatus());
        assertEquals(2, movieImportReportResponse.rows().get(0).errors().size());
        assertEquals(ImportRowStatus.CREATED, movieImportReportResponse.rows().get(1).rowStatus());
        assertEquals(1, movieImportReportResponse.unmatchedCastErrors().size());
        assertEquals(
                2,
                movieImportWorkbook.read(movieImportService.exportRows(content, List.of(2))).castRows().size());
    }

    @Test
    void sampleRowsAreNeverImported() throws IOException {
        MovieImportReportResponse movieImportReportResponse = movieImportService.previewImport(
                workbook(HEADERS, List.of(validRow("[Mẫu] Mai"), validRow("Tết"))));

        assertEquals(1, movieImportReportResponse.totalRows());
        assertEquals(3, movieImportReportResponse.rows().getFirst().rowNumber());
    }

    @Test
    void untouchedTemplateHasNoMovieToImport() {
        AppException appException = assertThrows(
                AppException.class, () -> movieImportWorkbook.read(movieImportService.buildTemplate()));

        assertEquals("EMPTY_IMPORT_FILE", appException.getErrorCode());
    }

    @Test
    void realExcelDateAndNumberCellsAreUnderstood() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(movieImportService.buildTemplate()));
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            CellStyle dateCellStyle = workbook.createCellStyle();
            dateCellStyle.setDataFormat((short) 14);
            Row row = workbook.getSheet("Phim").getRow(3);
            row.createCell(MovieImportColumn.TITLE.ordinal()).setCellValue("Mai");
            row.createCell(MovieImportColumn.DURATION.ordinal()).setCellValue(120);
            row.createCell(MovieImportColumn.RELEASE_DATE.ordinal()).setCellValue(LocalDate.of(2026, 12, 5));
            row.getCell(MovieImportColumn.RELEASE_DATE.ordinal()).setCellStyle(dateCellStyle);
            row.createCell(MovieImportColumn.RATED.ordinal()).setCellValue("P");
            row.createCell(MovieImportColumn.STATUS.ordinal()).setCellValue("Sắp chiếu");
            workbook.write(byteArrayOutputStream);

            MovieImportRowResponse movieImportRowResponse = movieImportService
                    .previewImport(byteArrayOutputStream.toByteArray()).rows().getFirst();

            assertEquals(ImportRowStatus.VALID, movieImportRowResponse.rowStatus());
            assertEquals(120, movieImportRowResponse.durationMinutes());
            assertEquals(LocalDate.of(2026, 12, 5), movieImportRowResponse.releaseDate());
            assertEquals(MovieStatus.COMING_SOON, movieImportRowResponse.status());
        }
    }

    @Test
    void filesWithoutRequiredColumnsOrNotExcelAreRejected() throws IOException {
        byte[] content = workbook(List.of("Tên phim"), List.of(List.of("Mai")));

        assertEquals(
                "INVALID_IMPORT_TEMPLATE",
                assertThrows(AppException.class, () -> movieImportService.previewImport(content)).getErrorCode());
        assertEquals(
                "INVALID_IMPORT_FILE",
                assertThrows(AppException.class, () -> movieImportService.previewImport(new byte[] {1, 2, 3}))
                        .getErrorCode());
    }

    @Test
    void moreThanTheRowLimitIsRejected() throws IOException {
        byte[] content = workbook(HEADERS, Collections.nCopies(201, validRow("Mai")));

        assertEquals(
                "TOO_MANY_IMPORT_ROWS",
                assertThrows(AppException.class, () -> movieImportService.previewImport(content)).getErrorCode());
    }
}
