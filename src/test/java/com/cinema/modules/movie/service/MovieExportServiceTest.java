package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.movie.entity.Genre;
import com.cinema.modules.movie.entity.Movie;
import com.cinema.modules.movie.entity.MovieCast;
import com.cinema.modules.movie.entity.Person;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.CastRoleType;
import com.cinema.modules.movie.enums.MovieCastColumn;
import com.cinema.modules.movie.enums.MovieImportColumn;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.model.MovieImportContent;
import com.cinema.modules.movie.model.MovieImportSheetRow;
import com.cinema.modules.movie.repository.GenreRepository;
import com.cinema.modules.movie.repository.MovieCastRepository;
import com.cinema.modules.movie.repository.MovieRepository;
import com.cinema.modules.movie.repository.PersonRepository;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieExportServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private MovieCastRepository movieCastRepository;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private PersonRepository personRepository;

    private MovieExportService movieExportService;

    @BeforeEach
    void setUp() {
        movieExportService = new MovieExportService(
                movieRepository, movieCastRepository, genreRepository, personRepository, new ExportWorkbook());
    }

    private static Genre genre(String name, String slug) {
        Genre genre = new Genre();
        genre.setName(name);
        genre.setSlug(slug);
        return genre;
    }

    @Test
    void exportedMoviesCanBeReadBackByTheImporter() throws IOException {
        Movie movie = new Movie();
        movie.setId(1L);
        movie.setTitle("=Mai");
        movie.setSlug("mai");
        movie.setDurationMinutes(131);
        movie.setReleaseDate(LocalDate.of(2026, 2, 10));
        movie.setRated(AgeRating.C18);
        movie.setStatus(MovieStatus.NOW_SHOWING);
        movie.setGenres(Set.of(genre("A", "a"), genre("B", "b"), genre("C", "c"), genre("D", "d")));
        Person person = new Person();
        person.setName("Trấn Thành");
        MovieCast movieCast = new MovieCast();
        movieCast.setMovieId(1L);
        movieCast.setPerson(person);
        movieCast.setRoleType(CastRoleType.DIRECTOR);
        when(movieRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(movie));
        when(movieRepository.findWithGenresByIdIn(anyCollection())).thenReturn(List.of(movie));
        when(movieCastRepository.findByMovieIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(movieCast));

        byte[] content = movieExportService.exportMovies(null, null, null, null, false);

        MovieImportContent movieImportContent = new MovieImportWorkbook().read(content);
        MovieImportSheetRow movieImportSheetRow = movieImportContent.movieRows().getFirst();
        assertEquals("=Mai", movieImportSheetRow.value(MovieImportColumn.TITLE));
        assertEquals("131", movieImportSheetRow.value(MovieImportColumn.DURATION));
        assertEquals("10/02/2026", movieImportSheetRow.value(MovieImportColumn.RELEASE_DATE));
        assertEquals("Cấm khán giả dưới 18 tuổi", movieImportSheetRow.value(MovieImportColumn.RATED));
        assertEquals("Đang chiếu", movieImportSheetRow.value(MovieImportColumn.STATUS));
        assertEquals("D", movieImportSheetRow.value(MovieImportColumn.OTHER_GENRES));
        assertEquals("Đạo diễn", movieImportContent.castRows().getFirst().value(MovieCastColumn.ROLE));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheet("Phim");
            assertEquals(CellType.STRING, sheet.getRow(1).getCell(0).getCellType());
            assertEquals(CellType.NUMERIC, sheet.getRow(1).getCell(MovieImportColumn.DURATION.ordinal()).getCellType());
            assertEquals("Times New Roman", sheet.getRow(1).getCell(0).getCellStyle() instanceof XSSFCellStyle cellStyle
                    ? cellStyle.getFont().getFontName()
                    : "");
        }
    }

    @Test
    void genresFollowTheSearchKeywordWithoutAccents() throws IOException {
        when(genreRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(genre("Hành Động", "hanh-dong"), genre("Kinh Dị", "kinh-di")));

        try (XSSFWorkbook workbook =
                new XSSFWorkbook(new ByteArrayInputStream(movieExportService.exportGenres("Hành")))) {
            Sheet sheet = workbook.getSheet("Thể loại");
            assertEquals(1, sheet.getLastRowNum());
            assertEquals("Hành Động", sheet.getRow(1).getCell(0).getStringCellValue());
        }
    }

    @Test
    void anOversizedMovieListIsRefused() {
        Movie movie = new Movie();
        movie.setId(1L);
        when(movieRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(Collections.nCopies(MovieExportService.MAX_EXPORT_ROWS + 1, movie));

        AppException appException = assertThrows(
                AppException.class, () -> movieExportService.exportMovies(null, null, null, null, false));

        assertEquals("TOO_MANY_EXPORT_ROWS", appException.getErrorCode());
    }
}
