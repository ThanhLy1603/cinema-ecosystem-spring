package com.cinema.modules.movie.model;

import com.cinema.modules.movie.enums.MovieImportColumn;

import java.util.Map;

public record MovieImportSheetRow(int rowNumber, Map<MovieImportColumn, String> values) {

    public String value(MovieImportColumn movieImportColumn) {
        return values.getOrDefault(movieImportColumn, "");
    }
}
