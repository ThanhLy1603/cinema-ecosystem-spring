package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.util.SlugGenerator;
import com.cinema.modules.movie.entity.Genre;
import com.cinema.modules.movie.entity.Movie;
import com.cinema.modules.movie.entity.MovieCast;
import com.cinema.modules.movie.entity.Person;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.CastRoleType;
import com.cinema.modules.movie.enums.MovieCastColumn;
import com.cinema.modules.movie.enums.MovieImportColumn;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.model.ExportColumn;
import com.cinema.modules.movie.model.ExportSheet;
import com.cinema.modules.movie.repository.GenreRepository;
import com.cinema.modules.movie.repository.MovieCastRepository;
import com.cinema.modules.movie.repository.MovieRepository;
import com.cinema.modules.movie.repository.PersonRepository;
import com.cinema.modules.movie.service.interfaces.IMovieExportService;
import com.cinema.modules.movie.specification.MovieSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovieExportService implements IMovieExportService {

    static final int MAX_EXPORT_ROWS = 2000;
    private static final Map<MovieStatus, String> STATUS_LABELS = Map.of(
            MovieStatus.DRAFT, "Bản nháp",
            MovieStatus.COMING_SOON, "Sắp chiếu",
            MovieStatus.NOW_SHOWING, "Đang chiếu",
            MovieStatus.ENDED, "Ngừng chiếu");
    private static final Map<CastRoleType, String> ROLE_LABELS = Map.of(
            CastRoleType.DIRECTOR, "Đạo diễn",
            CastRoleType.ACTOR, "Diễn viên",
            CastRoleType.PRODUCER, "Nhà sản xuất");

    private final MovieRepository movieRepository;
    private final MovieCastRepository movieCastRepository;
    private final GenreRepository genreRepository;
    private final PersonRepository personRepository;
    private final ExportWorkbook exportWorkbook;

    @Transactional(readOnly = true)
    @Override
    public byte[] exportMovies(
            String keyword, Long genreId, MovieStatus status, AgeRating rated, boolean deleted) {
        List<Long> movieIds = movieRepository
                .findAll(
                        MovieSpecifications.search(keyword, genreId, status, rated, deleted),
                        Sort.by(Sort.Order.desc("releaseDate"), Sort.Order.desc("id")))
                .stream()
                .map(Movie::getId)
                .toList();
        requireExportable(movieIds.size());
        Map<Long, Movie> moviesById = movieRepository.findWithGenresByIdIn(movieIds).stream()
                .collect(Collectors.toMap(Movie::getId, Function.identity()));
        List<Movie> movies = movieIds.stream().map(moviesById::get).toList();
        Map<Long, List<MovieCast>> movieCastsByMovieId =
                movieCastRepository.findByMovieIdInOrderByIdAsc(movieIds).stream()
                        .collect(Collectors.groupingBy(MovieCast::getMovieId));
        return exportWorkbook.write(List.of(
                toMovieSheet(movies), toCastSheet(movies, movieCastsByMovieId)));
    }

    @Transactional(readOnly = true)
    @Override
    public byte[] exportGenres(String keyword) {
        String slugKeyword = keyword == null || keyword.isBlank() ? "" : SlugGenerator.toSlug(keyword, "");
        List<List<Object>> rows = genreRepository.findAllByOrderByNameAsc().stream()
                .filter(genre -> genre.getSlug().contains(slugKeyword))
                .map(genre -> Arrays.<Object>asList(genre.getName(), genre.getSlug()))
                .toList();
        return exportWorkbook.write(List.of(new ExportSheet(
                "Thể loại",
                List.of(new ExportColumn("Tên thể loại", 40), new ExportColumn("Đường dẫn", 40)),
                rows)));
    }

    @Transactional(readOnly = true)
    @Override
    public byte[] exportPersons(String keyword) {
        List<Person> persons = personRepository
                .findByNameContainingIgnoreCase(
                        keyword == null ? "" : keyword.trim(),
                        PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by("name", "id")))
                .getContent();
        List<List<Object>> rows = persons.stream()
                .map(person -> Arrays.<Object>asList(person.getName(), person.getAvatarUrl(), person.getBio()))
                .toList();
        return exportWorkbook.write(List.of(new ExportSheet(
                "Nghệ sĩ",
                List.of(
                        new ExportColumn("Tên nghệ sĩ", 36),
                        new ExportColumn("Ảnh đại diện", 50),
                        new ExportColumn("Tiểu sử", 70)),
                rows)));
    }

    private static void requireExportable(int rowCount) {
        if (rowCount > MAX_EXPORT_ROWS) {
            throw new AppException(
                    "Danh sách có hơn " + MAX_EXPORT_ROWS + " dòng, hãy lọc bớt rồi xuất",
                    HttpStatus.BAD_REQUEST,
                    "TOO_MANY_EXPORT_ROWS");
        }
    }

    private static ExportSheet toMovieSheet(List<Movie> movies) {
        return new ExportSheet(
                "Phim",
                Arrays.stream(MovieImportColumn.values())
                        .map(movieImportColumn ->
                                new ExportColumn(movieImportColumn.getHeader(), movieImportColumn.getWidth()))
                        .toList(),
                movies.stream().map(MovieExportService::toMovieValues).toList());
    }

    private static List<Object> toMovieValues(Movie movie) {
        List<String> genreNames = movie.getGenres().stream()
                .map(Genre::getName)
                .sorted(Comparator.naturalOrder())
                .toList();
        return Arrays.stream(MovieImportColumn.values())
                .map(movieImportColumn -> (Object) switch (movieImportColumn) {
                    case TITLE -> movie.getTitle();
                    case SLUG -> movie.getSlug();
                    case ORIGINAL_TITLE -> movie.getOriginalTitle();
                    case DURATION -> Integer.valueOf(movie.getDurationMinutes());
                    case RELEASE_DATE -> movie.getReleaseDate();
                    case END_DATE -> movie.getEndDate();
                    case RATED -> movie.getRated().getDescription();
                    case STATUS -> STATUS_LABELS.get(movie.getStatus());
                    case GENRE_1 -> genreNames.size() > 0 ? genreNames.get(0) : null;
                    case GENRE_2 -> genreNames.size() > 1 ? genreNames.get(1) : null;
                    case GENRE_3 -> genreNames.size() > 2 ? genreNames.get(2) : null;
                    case OTHER_GENRES -> genreNames.size() > 3
                            ? String.join("; ", genreNames.subList(3, genreNames.size()))
                            : null;
                    case DESCRIPTION -> movie.getDescription();
                    case POSTER -> movie.getPosterUrl();
                    case BANNER -> movie.getBannerUrl();
                    case TRAILER -> movie.getTrailerUrl();
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static ExportSheet toCastSheet(List<Movie> movies, Map<Long, List<MovieCast>> movieCastsByMovieId) {
        List<List<Object>> rows = movies.stream()
                .flatMap(movie -> movieCastsByMovieId.getOrDefault(movie.getId(), List.of()).stream()
                        .map(movieCast -> Arrays.<Object>asList(
                                movie.getTitle(),
                                movieCast.getPerson().getName(),
                                ROLE_LABELS.get(movieCast.getRoleType()),
                                movieCast.getCharacterName())))
                .toList();
        return new ExportSheet(
                MovieImportWorkbook.CAST_SHEET_NAME,
                Arrays.stream(MovieCastColumn.values())
                        .map(movieCastColumn ->
                                new ExportColumn(movieCastColumn.getHeader(), movieCastColumn.getWidth()))
                        .toList(),
                rows);
    }
}
