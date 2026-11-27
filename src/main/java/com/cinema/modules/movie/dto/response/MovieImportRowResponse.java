package com.cinema.modules.movie.dto.response;

import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.ImportRowStatus;
import com.cinema.modules.movie.enums.MovieStatus;

import java.time.LocalDate;
import java.util.List;

public record MovieImportRowResponse(
        int rowNumber,
        String title,
        String slug,
        Integer durationMinutes,
        LocalDate releaseDate,
        AgeRating rated,
        MovieStatus status,
        List<String> genreNames,
        int castCount,
        List<String> newPersonNames,
        ImportRowStatus rowStatus,
        List<String> errors) {
}
