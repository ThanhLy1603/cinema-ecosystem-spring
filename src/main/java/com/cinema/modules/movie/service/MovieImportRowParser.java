package com.cinema.modules.movie.service;

import com.cinema.modules.movie.dto.request.MovieRequest;
import com.cinema.modules.movie.entity.Genre;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieImportColumn;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.model.MovieImportSheetRow;
import com.cinema.modules.movie.model.ParsedMovieImportRow;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MovieImportRowParser {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ISO_LOCAL_DATE);
    private static final List<MovieImportColumn> GENRE_COLUMNS = List.of(
            MovieImportColumn.GENRE_1,
            MovieImportColumn.GENRE_2,
            MovieImportColumn.GENRE_3,
            MovieImportColumn.OTHER_GENRES);
    private static final Map<String, MovieStatus> STATUS_BY_LABEL = Map.of(
            "bản nháp", MovieStatus.DRAFT,
            "sắp chiếu", MovieStatus.COMING_SOON,
            "đang chiếu", MovieStatus.NOW_SHOWING,
            "ngừng chiếu", MovieStatus.ENDED);

    private final Validator validator;

    public ParsedMovieImportRow parse(MovieImportSheetRow movieImportSheetRow, List<Genre> genres) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        Integer durationMinutes = parseDuration(movieImportSheetRow, fieldErrors);
        LocalDate releaseDate = parseDate(movieImportSheetRow, MovieImportColumn.RELEASE_DATE, fieldErrors);
        LocalDate endDate = parseDate(movieImportSheetRow, MovieImportColumn.END_DATE, fieldErrors);
        AgeRating ageRating = parseAgeRating(movieImportSheetRow, fieldErrors);
        MovieStatus movieStatus = parseStatus(movieImportSheetRow, fieldErrors);
        List<Genre> selectedGenres = parseGenres(movieImportSheetRow, genres, fieldErrors);
        if (releaseDate != null && endDate != null && endDate.isBefore(releaseDate)) {
            fieldErrors.put(MovieImportColumn.END_DATE.getFieldName(),
                    "Ngày kết thúc chiếu phải sau hoặc bằng ngày khởi chiếu");
        }
        MovieRequest movieRequest = new MovieRequest(
                movieImportSheetRow.value(MovieImportColumn.TITLE),
                movieImportSheetRow.value(MovieImportColumn.SLUG),
                movieImportSheetRow.value(MovieImportColumn.ORIGINAL_TITLE),
                durationMinutes,
                releaseDate,
                endDate,
                ageRating,
                movieImportSheetRow.value(MovieImportColumn.POSTER),
                movieImportSheetRow.value(MovieImportColumn.BANNER),
                movieImportSheetRow.value(MovieImportColumn.TRAILER),
                movieImportSheetRow.value(MovieImportColumn.DESCRIPTION),
                movieStatus,
                selectedGenres.stream().map(Genre::getId).toList(),
                List.of());
        validator.validate(movieRequest).stream()
                .sorted((firstConstraintViolation, secondConstraintViolation) ->
                        firstConstraintViolation.getMessage().compareTo(secondConstraintViolation.getMessage()))
                .forEach(constraintViolation -> addViolation(fieldErrors, constraintViolation));
        return new ParsedMovieImportRow(
                movieRequest,
                selectedGenres.stream().map(Genre::getName).toList(),
                new ArrayList<>(fieldErrors.values()));
    }

    private static void addViolation(
            Map<String, String> fieldErrors, ConstraintViolation<MovieRequest> constraintViolation) {
        fieldErrors.putIfAbsent(constraintViolation.getPropertyPath().toString(), constraintViolation.getMessage());
    }

    private static Integer parseDuration(MovieImportSheetRow movieImportSheetRow, Map<String, String> fieldErrors) {
        String value = movieImportSheetRow.value(MovieImportColumn.DURATION);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException numberFormatException) {
            fieldErrors.put(MovieImportColumn.DURATION.getFieldName(), "Thời lượng phải là số phút nguyên");
            return null;
        }
    }

    private static LocalDate parseDate(
            MovieImportSheetRow movieImportSheetRow,
            MovieImportColumn movieImportColumn,
            Map<String, String> fieldErrors) {
        String value = movieImportSheetRow.value(movieImportColumn);
        if (value.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter dateTimeFormatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, dateTimeFormatter);
            } catch (DateTimeParseException dateTimeParseException) {
                continue;
            }
        }
        fieldErrors.put(movieImportColumn.getFieldName(),
                movieImportColumn.getHeader() + " không hợp lệ, hãy nhập dạng ngày/tháng/năm");
        return null;
    }

    private static AgeRating parseAgeRating(MovieImportSheetRow movieImportSheetRow, Map<String, String> fieldErrors) {
        String value = movieImportSheetRow.value(MovieImportColumn.RATED);
        if (value.isEmpty()) {
            return null;
        }
        return Arrays.stream(AgeRating.values())
                .filter(ageRating -> normalizeName(ageRating.getDescription()).equals(normalizeName(value))
                        || ageRating.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseGet(() -> {
                    fieldErrors.put(MovieImportColumn.RATED.getFieldName(), "Nhãn độ tuổi không hợp lệ, hãy chọn trong danh sách xổ xuống");
                    return null;
                });
    }

    private static MovieStatus parseStatus(MovieImportSheetRow movieImportSheetRow, Map<String, String> fieldErrors) {
        String value = movieImportSheetRow.value(MovieImportColumn.STATUS);
        if (value.isEmpty()) {
            return MovieStatus.DRAFT;
        }
        MovieStatus movieStatus = STATUS_BY_LABEL.get(value.toLowerCase(Locale.ROOT));
        if (movieStatus == null) {
            fieldErrors.put(MovieImportColumn.STATUS.getFieldName(),
                    "Trạng thái chỉ nhận Bản nháp, Sắp chiếu, Đang chiếu hoặc Ngừng chiếu");
        }
        return movieStatus;
    }

    private static List<Genre> parseGenres(
            MovieImportSheetRow movieImportSheetRow, List<Genre> genres, Map<String, String> fieldErrors) {
        Map<String, Genre> genresByName = genres.stream()
                .collect(Collectors.toMap(
                        genre -> normalizeName(genre.getName()), Function.identity(), (firstGenre, secondGenre) -> firstGenre));
        List<Genre> selectedGenres = new ArrayList<>();
        List<String> unknownNames = new ArrayList<>();
        String genreNames = GENRE_COLUMNS.stream()
                .map(movieImportSheetRow::value)
                .collect(Collectors.joining(";"));
        for (String genreName : genreNames.split(";")) {
            if (genreName.isBlank()) {
                continue;
            }
            Genre genre = genresByName.get(normalizeName(genreName));
            if (genre == null) {
                unknownNames.add(genreName.strip());
            } else if (!selectedGenres.contains(genre)) {
                selectedGenres.add(genre);
            }
        }
        if (!unknownNames.isEmpty()) {
            fieldErrors.put(MovieImportColumn.GENRE_1.getFieldName(),
                    "Chưa có thể loại: " + String.join(", ", unknownNames) + ". Hãy chọn trong danh sách ở sheet Thể loại");
        }
        return selectedGenres;
    }

    private static String normalizeName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFC).strip().toLowerCase(Locale.ROOT);
    }
}
