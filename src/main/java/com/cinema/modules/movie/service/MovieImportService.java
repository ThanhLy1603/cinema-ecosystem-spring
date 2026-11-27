package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.movie.dto.request.MovieCastRequest;
import com.cinema.modules.movie.dto.request.MovieRequest;
import com.cinema.modules.movie.dto.request.PersonRequest;
import com.cinema.modules.movie.dto.response.MovieImportReportResponse;
import com.cinema.modules.movie.dto.response.MovieImportRowResponse;
import com.cinema.modules.movie.entity.Genre;
import com.cinema.modules.movie.entity.Person;
import com.cinema.modules.movie.enums.MovieCastColumn;
import com.cinema.modules.movie.enums.MovieImportColumn;
import com.cinema.modules.movie.enums.ImportRowStatus;
import com.cinema.modules.movie.model.MovieCastPlan;
import com.cinema.modules.movie.model.MovieImportCastResolution;
import com.cinema.modules.movie.model.MovieImportContent;
import com.cinema.modules.movie.model.MovieImportSheetRow;
import com.cinema.modules.movie.model.ParsedMovieImportRow;
import com.cinema.modules.movie.model.PlannedMovieCast;
import com.cinema.modules.movie.repository.PersonRepository;
import com.cinema.modules.movie.repository.GenreRepository;
import com.cinema.modules.movie.repository.MovieRepository;
import com.cinema.modules.movie.service.interfaces.IMovieAdminService;
import com.cinema.modules.movie.service.interfaces.IMovieImportService;
import com.cinema.modules.movie.service.interfaces.IPersonAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovieImportService implements IMovieImportService {

    private final MovieImportWorkbook movieImportWorkbook;
    private final MovieImportRowParser movieImportRowParser;
    private final MovieSlugResolver movieSlugResolver;
    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final IMovieAdminService movieAdminService;
    private final MovieImportCastResolver movieImportCastResolver;
    private final PersonRepository personRepository;
    private final IPersonAdminService personAdminService;

    @Override
    public byte[] buildTemplate() {
        return movieImportWorkbook.writeTemplate(findGenreNames(), findPersonNames(findPersons()));
    }

    @Override
    public MovieImportReportResponse previewImport(byte[] content) {
        Analysis analysis = analyze(movieImportWorkbook.read(content));
        return toReport(analysis.analyzedRows(), analysis);
    }

    @Override
    public MovieImportReportResponse importMovies(byte[] content) {
        Analysis analysis = analyze(movieImportWorkbook.read(content));
        Map<String, Long> createdPersonIds = new HashMap<>();
        return toReport(
                analysis.analyzedRows().stream()
                        .map(analyzedRow -> createWhenValid(analyzedRow, createdPersonIds))
                        .toList(),
                analysis);
    }

    @Override
    public byte[] exportRows(byte[] content, Collection<Integer> rowNumbers) {
        MovieImportContent movieImportContent = movieImportWorkbook.read(content);
        Map<Integer, List<String>> errorsByRowNumber = analyze(movieImportContent).analyzedRows().stream()
                .collect(Collectors.toMap(
                        analyzedRow -> analyzedRow.movieImportSheetRow().rowNumber(), AnalyzedRow::errors));
        List<MovieImportSheetRow> movieImportSheetRows = movieImportContent.movieRows().stream()
                .filter(movieImportSheetRow -> rowNumbers.contains(movieImportSheetRow.rowNumber()))
                .toList();
        Set<String> exportedTitles = movieImportSheetRows.stream()
                .map(movieImportSheetRow -> MovieImportCastResolver.normalizeName(
                        movieImportSheetRow.value(MovieImportColumn.TITLE)))
                .collect(Collectors.toSet());
        return movieImportWorkbook.writeRejectedRows(
                new MovieImportContent(
                        movieImportSheetRows,
                        movieImportContent.castRows().stream()
                                .filter(movieImportCastRow -> exportedTitles.contains(MovieImportCastResolver
                                        .normalizeName(movieImportCastRow.value(MovieCastColumn.MOVIE_TITLE))))
                                .toList()),
                errorsByRowNumber,
                findGenreNames(),
                findPersonNames(findPersons()));
    }

    private static MovieImportReportResponse toReport(List<AnalyzedRow> analyzedRows, Analysis analysis) {
        return MovieImportReportResponse.of(
                analyzedRows.stream().map(AnalyzedRow::toResponse).toList(), analysis.unmatchedCastErrors());
    }

    private List<String> findGenreNames() {
        return genreRepository.findAllByOrderByNameAsc().stream().map(Genre::getName).toList();
    }

    private List<Person> findPersons() {
        return personRepository.findAll(Sort.by("name", "id"));
    }

    private static List<String> findPersonNames(List<Person> persons) {
        return persons.stream().map(Person::getName).distinct().toList();
    }

    private Analysis analyze(MovieImportContent movieImportContent) {
        List<Genre> genres = genreRepository.findAllByOrderByNameAsc();
        MovieImportCastResolution movieImportCastResolution =
                movieImportCastResolver.resolve(movieImportContent, findPersons());
        Map<String, Integer> rowNumbersBySlug = new HashMap<>();
        List<AnalyzedRow> analyzedRows = new ArrayList<>();
        for (MovieImportSheetRow movieImportSheetRow : movieImportContent.movieRows()) {
            analyzedRows.add(analyzeRow(
                    movieImportSheetRow,
                    genres,
                    movieImportCastResolution.planFor(movieImportSheetRow.rowNumber()),
                    rowNumbersBySlug));
        }
        return new Analysis(analyzedRows, movieImportCastResolution.unmatchedErrors());
    }

    private AnalyzedRow analyzeRow(
            MovieImportSheetRow movieImportSheetRow,
            List<Genre> genres,
            MovieCastPlan movieCastPlan,
            Map<String, Integer> rowNumbersBySlug) {
        ParsedMovieImportRow parsedMovieImportRow = movieImportRowParser.parse(movieImportSheetRow, genres);
        MovieRequest movieRequest = parsedMovieImportRow.movieRequest();
        boolean hasRequestedSlug = !movieRequest.slug().isEmpty();
        String slug = hasRequestedSlug || movieRequest.title().isEmpty()
                ? movieRequest.slug()
                : movieSlugResolver.proposeSlug(movieRequest.title());
        List<String> errors = new ArrayList<>(parsedMovieImportRow.errors());
        errors.addAll(movieCastPlan.errors());
        AnalyzedRow analyzedRow = new AnalyzedRow(
                movieImportSheetRow,
                parsedMovieImportRow,
                movieCastPlan,
                slug,
                ImportRowStatus.VALID,
                errors);
        if (!errors.isEmpty()) {
            return analyzedRow.withOutcome(ImportRowStatus.ERROR, errors);
        }
        Integer firstRowNumber = rowNumbersBySlug.putIfAbsent(slug, movieImportSheetRow.rowNumber());
        if (firstRowNumber != null) {
            return analyzedRow.withOutcome(
                    ImportRowStatus.DUPLICATE, List.of("Trùng phim với dòng " + firstRowNumber));
        }
        if (movieRepository.existsBySlugAndDeletedFalse(slug)) {
            return analyzedRow.withOutcome(
                    ImportRowStatus.DUPLICATE, List.of("Đã có phim dùng đường dẫn " + slug));
        }
        if (!hasRequestedSlug && movieRepository.existsByTitleIgnoreCaseAndDeletedFalse(movieRequest.title())) {
            return analyzedRow.withOutcome(ImportRowStatus.DUPLICATE, List.of("Đã có phim trùng tên"));
        }
        return analyzedRow;
    }

    private AnalyzedRow createWhenValid(AnalyzedRow analyzedRow, Map<String, Long> createdPersonIds) {
        if (analyzedRow.importRowStatus() != ImportRowStatus.VALID) {
            return analyzedRow;
        }
        try {
            List<MovieCastRequest> movieCastRequests = analyzedRow.movieCastPlan().plannedMovieCasts().stream()
                    .map(plannedMovieCast -> new MovieCastRequest(
                            resolvePersonId(plannedMovieCast, createdPersonIds),
                            plannedMovieCast.roleType(),
                            plannedMovieCast.characterName()))
                    .toList();
            movieAdminService.createMovie(analyzedRow.toMovieRequest(movieCastRequests));
        } catch (AppException appException) {
            return analyzedRow.withOutcome(ImportRowStatus.ERROR, List.of(appException.getMessage()));
        }
        return analyzedRow.withOutcome(ImportRowStatus.CREATED, List.of());
    }

    private Long resolvePersonId(PlannedMovieCast plannedMovieCast, Map<String, Long> createdPersonIds) {
        if (plannedMovieCast.personId() != null) {
            return plannedMovieCast.personId();
        }
        return createdPersonIds.computeIfAbsent(
                MovieImportCastResolver.normalizeName(plannedMovieCast.personName()),
                key -> personAdminService
                        .createPerson(new PersonRequest(plannedMovieCast.personName(), null, null))
                        .id());
    }

    private record Analysis(List<AnalyzedRow> analyzedRows, List<String> unmatchedCastErrors) {
    }

    private record AnalyzedRow(
            MovieImportSheetRow movieImportSheetRow,
            ParsedMovieImportRow parsedMovieImportRow,
            MovieCastPlan movieCastPlan,
            String slug,
            ImportRowStatus importRowStatus,
            List<String> errors) {

        AnalyzedRow withOutcome(ImportRowStatus nextImportRowStatus, List<String> nextErrors) {
            return new AnalyzedRow(
                    movieImportSheetRow,
                    parsedMovieImportRow,
                    movieCastPlan,
                    slug,
                    nextImportRowStatus,
                    nextErrors);
        }

        MovieRequest toMovieRequest(List<MovieCastRequest> movieCastRequests) {
            MovieRequest movieRequest = parsedMovieImportRow.movieRequest();
            return new MovieRequest(
                    movieRequest.title(),
                    slug,
                    movieRequest.originalTitle(),
                    movieRequest.durationMinutes(),
                    movieRequest.releaseDate(),
                    movieRequest.endDate(),
                    movieRequest.rated(),
                    movieRequest.posterUrl(),
                    movieRequest.bannerUrl(),
                    movieRequest.trailerUrl(),
                    movieRequest.description(),
                    movieRequest.status(),
                    movieRequest.genreIds(),
                    movieCastRequests);
        }

        MovieImportRowResponse toResponse() {
            MovieRequest movieRequest = parsedMovieImportRow.movieRequest();
            return new MovieImportRowResponse(
                    movieImportSheetRow.rowNumber(),
                    movieRequest.title(),
                    slug,
                    movieRequest.durationMinutes(),
                    movieRequest.releaseDate(),
                    movieRequest.rated(),
                    movieRequest.status(),
                    parsedMovieImportRow.genreNames(),
                    movieCastPlan.plannedMovieCasts().size(),
                    movieCastPlan.newPersonNames(),
                    importRowStatus,
                    errors);
        }
    }
}
