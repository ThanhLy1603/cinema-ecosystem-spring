package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.movie.dto.request.GenreRequest;
import com.cinema.modules.movie.dto.request.PersonRequest;
import com.cinema.modules.movie.dto.response.CatalogImportReportResponse;
import com.cinema.modules.movie.entity.Genre;
import com.cinema.modules.movie.enums.CatalogImportType;
import com.cinema.modules.movie.repository.GenreRepository;
import com.cinema.modules.movie.repository.PersonRepository;
import com.cinema.modules.movie.service.interfaces.IGenreAdminService;
import com.cinema.modules.movie.service.interfaces.IPersonAdminService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogImportServiceTest {

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private IGenreAdminService genreAdminService;

    @Mock
    private IPersonAdminService personAdminService;

    private CatalogImportService catalogImportService;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        catalogImportService = new CatalogImportService(
                new CatalogImportWorkbook(),
                List.of(
                        new GenreImportDefinition(genreRepository, genreAdminService, validator),
                        new PersonImportDefinition(personRepository, personAdminService, validator)));
        Genre genre = new Genre();
        genre.setName("Hành Động");
        when(genreRepository.findAllByOrderByNameAsc()).thenReturn(List.of(genre));
        when(personRepository.findAll(any(Sort.class))).thenReturn(List.of());
    }

    private byte[] filled(CatalogImportType catalogImportType, List<List<String>> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                        new ByteArrayInputStream(catalogImportService.buildTemplate(catalogImportType)));
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 0; i < rows.size(); i++) {
                Row row = sheet.getRow(3 + i);
                for (int j = 0; j < rows.get(i).size(); j++) {
                    row.getCell(j).setCellValue(rows.get(i).get(j));
                }
            }
            workbook.write(byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        }
    }

    @Test
    void genresAreCreatedOnceAndExistingNamesAreSkipped() throws IOException {
        byte[] content = filled(
                CatalogImportType.GENRES,
                List.of(List.of("Cổ Trang"), List.of("cổ trang"), List.of("HÀNH ĐỘNG"), List.of("a".repeat(101))));

        CatalogImportReportResponse previewReport =
                catalogImportService.previewImport(CatalogImportType.GENRES, content);

        assertEquals(1, previewReport.validRows());
        assertEquals(2, previewReport.duplicateRows());
        assertEquals(1, previewReport.errorRows());
        verify(genreAdminService, never()).createGenre(any());

        assertEquals(1, catalogImportService.importRows(CatalogImportType.GENRES, content).createdRows());
        verify(genreAdminService).createGenre(new GenreRequest("Cổ Trang"));
    }

    @Test
    void artistsKeepTheirPictureAndBiography() throws IOException {
        byte[] content = filled(
                CatalogImportType.PERSONS,
                List.of(
                        List.of("Lý Hải", "https://cdn/ly-hai.jpg", "Đạo diễn"),
                        List.of("Nana", "sai-duong-dan", "")));

        CatalogImportReportResponse catalogImportReportResponse =
                catalogImportService.importRows(CatalogImportType.PERSONS, content);

        assertEquals(1, catalogImportReportResponse.createdRows());
        assertEquals(1, catalogImportReportResponse.errorRows());
        verify(personAdminService).createPerson(new PersonRequest("Lý Hải", "https://cdn/ly-hai.jpg", "Đạo diễn"));
    }

    @Test
    void untouchedTemplateHasNothingToImportAndMarksRequiredColumns() throws IOException {
        byte[] template = catalogImportService.buildTemplate(CatalogImportType.PERSONS);

        AppException appException = assertThrows(
                AppException.class, () -> catalogImportService.previewImport(CatalogImportType.PERSONS, template));

        assertEquals("EMPTY_IMPORT_FILE", appException.getErrorCode());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            XSSFRichTextString header = (XSSFRichTextString)
                    workbook.getSheet("Nghệ sĩ").getRow(0).getCell(0).getRichStringCellValue();
            assertEquals("Tên nghệ sĩ (*)", header.getString());
            assertEquals(IndexedColors.RED.getIndex(), header.getFontAtIndex(header.length() - 1).getColor());
        }
    }

    @Test
    void aGenreTemplateIsNotAcceptedAsArtists() {
        byte[] genreTemplate = catalogImportService.buildTemplate(CatalogImportType.GENRES);

        AppException appException = assertThrows(
                AppException.class,
                () -> catalogImportService.previewImport(CatalogImportType.PERSONS, genreTemplate));

        assertEquals("INVALID_IMPORT_TEMPLATE", appException.getErrorCode());
    }
}
