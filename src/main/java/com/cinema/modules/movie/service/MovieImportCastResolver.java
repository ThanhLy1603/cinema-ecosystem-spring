package com.cinema.modules.movie.service;

import com.cinema.modules.movie.entity.Person;
import com.cinema.modules.movie.enums.CastRoleType;
import com.cinema.modules.movie.enums.MovieCastColumn;
import com.cinema.modules.movie.enums.MovieImportColumn;
import com.cinema.modules.movie.model.MovieCastPlan;
import com.cinema.modules.movie.model.MovieImportCastResolution;
import com.cinema.modules.movie.model.MovieImportCastRow;
import com.cinema.modules.movie.model.MovieImportContent;
import com.cinema.modules.movie.model.MovieImportSheetRow;
import com.cinema.modules.movie.model.PlannedMovieCast;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class MovieImportCastResolver {

    static final int MAX_CASTS_PER_MOVIE = 60;
    private static final int MAX_NAME_LENGTH = 150;
    private static final Map<String, CastRoleType> ROLE_BY_LABEL = Map.of(
            "đạo diễn", CastRoleType.DIRECTOR,
            "diễn viên", CastRoleType.ACTOR,
            "nhà sản xuất", CastRoleType.PRODUCER);

    public static String normalizeName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFC).strip().toLowerCase(Locale.ROOT);
    }

    public MovieImportCastResolution resolve(MovieImportContent movieImportContent, List<Person> persons) {
        Map<String, List<MovieImportSheetRow>> movieRowsByTitle = movieImportContent.movieRows().stream()
                .collect(Collectors.groupingBy(movieImportSheetRow ->
                        normalizeName(movieImportSheetRow.value(MovieImportColumn.TITLE))));
        Map<String, List<Person>> personsByName =
                persons.stream().collect(Collectors.groupingBy(person -> normalizeName(person.getName())));
        Map<Integer, List<PlannedMovieCast>> plannedCastsByRowNumber = new HashMap<>();
        Map<Integer, List<String>> errorsByRowNumber = new HashMap<>();
        Map<Integer, Set<String>> castKeysByRowNumber = new HashMap<>();
        List<String> unmatchedErrors = new ArrayList<>();
        for (MovieImportCastRow movieImportCastRow : movieImportContent.castRows()) {
            String movieTitle = movieImportCastRow.value(MovieCastColumn.MOVIE_TITLE);
            List<MovieImportSheetRow> movieRows = movieRowsByTitle.get(normalizeName(movieTitle));
            String location = "Sheet " + MovieImportWorkbook.CAST_SHEET_NAME + " dòng " + movieImportCastRow.rowNumber();
            if (movieTitle.isEmpty() || movieRows == null) {
                unmatchedErrors.add(location + ": " + (movieTitle.isEmpty()
                        ? "chưa chọn phim"
                        : "không có phim tên " + movieTitle + " ở sheet Phim"));
                continue;
            }
            if (movieRows.stream().map(movieRow -> movieRow.value(MovieImportColumn.SLUG)).distinct().count() > 1) {
                movieRows.forEach(movieRow -> addError(errorsByRowNumber, movieRow.rowNumber(),
                        location + ": có nhiều phim cùng tên, không biết gán cho phim nào"));
                continue;
            }
            int movieRowNumber = movieRows.getFirst().rowNumber();
            resolveRow(
                    movieImportCastRow,
                    personsByName,
                    castKeysByRowNumber.computeIfAbsent(movieRowNumber, key -> new HashSet<>()),
                    plannedCastsByRowNumber.computeIfAbsent(movieRowNumber, key -> new ArrayList<>()),
                    error -> addError(errorsByRowNumber, movieRowNumber, location + ": " + error));
        }
        plannedCastsByRowNumber.forEach((movieRowNumber, plannedMovieCasts) -> {
            if (plannedMovieCasts.size() > MAX_CASTS_PER_MOVIE) {
                addError(errorsByRowNumber, movieRowNumber, "Mỗi phim tối đa " + MAX_CASTS_PER_MOVIE + " nghệ sĩ");
            }
        });
        Set<Integer> rowNumbers = new HashSet<>(plannedCastsByRowNumber.keySet());
        rowNumbers.addAll(errorsByRowNumber.keySet());
        return new MovieImportCastResolution(
                rowNumbers.stream().collect(Collectors.toMap(
                        rowNumber -> rowNumber,
                        rowNumber -> new MovieCastPlan(
                                plannedCastsByRowNumber.getOrDefault(rowNumber, List.of()),
                                errorsByRowNumber.getOrDefault(rowNumber, List.of())))),
                unmatchedErrors);
    }

    private static void addError(Map<Integer, List<String>> errorsByRowNumber, int rowNumber, String error) {
        errorsByRowNumber.computeIfAbsent(rowNumber, key -> new ArrayList<>()).add(error);
    }

    private static void resolveRow(
            MovieImportCastRow movieImportCastRow,
            Map<String, List<Person>> personsByName,
            Set<String> castKeys,
            List<PlannedMovieCast> plannedMovieCasts,
            Consumer<String> errorConsumer) {
        String personName = movieImportCastRow.value(MovieCastColumn.PERSON_NAME);
        String roleLabel = movieImportCastRow.value(MovieCastColumn.ROLE);
        String characterName = movieImportCastRow.value(MovieCastColumn.CHARACTER_NAME);
        CastRoleType castRoleType = ROLE_BY_LABEL.get(normalizeName(roleLabel));
        List<Person> matchedPersons = personsByName.getOrDefault(normalizeName(personName), List.of());
        if (personName.isEmpty()) {
            errorConsumer.accept("chưa nhập tên nghệ sĩ");
        } else if (personName.length() > MAX_NAME_LENGTH) {
            errorConsumer.accept("tên nghệ sĩ tối đa " + MAX_NAME_LENGTH + " ký tự");
        } else if (matchedPersons.size() > 1) {
            errorConsumer.accept("có nhiều nghệ sĩ cùng tên " + personName + ", hãy thêm vào phim bằng nút sửa");
        } else if (castRoleType == null) {
            errorConsumer.accept("vai trò chỉ nhận Đạo diễn, Diễn viên hoặc Nhà sản xuất");
        } else if (!characterName.isEmpty() && castRoleType != CastRoleType.ACTOR) {
            errorConsumer.accept("chỉ diễn viên mới có tên nhân vật");
        } else if (characterName.length() > MAX_NAME_LENGTH) {
            errorConsumer.accept("tên nhân vật tối đa " + MAX_NAME_LENGTH + " ký tự");
        } else if (!castKeys.add(normalizeName(personName) + ":" + castRoleType)) {
            errorConsumer.accept(personName + " bị lặp cùng một vai trò");
        } else {
            plannedMovieCasts.add(new PlannedMovieCast(
                    matchedPersons.isEmpty() ? null : matchedPersons.getFirst().getId(),
                    matchedPersons.isEmpty() ? personName : matchedPersons.getFirst().getName(),
                    castRoleType,
                    characterName.isEmpty() ? null : characterName));
        }
    }
}
