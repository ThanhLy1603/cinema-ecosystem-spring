package com.cinema.modules.movie.model;

import com.cinema.modules.movie.enums.MovieCastColumn;

import java.util.Map;

public record MovieImportCastRow(int rowNumber, Map<MovieCastColumn, String> values) {

    public String value(MovieCastColumn movieCastColumn) {
        return values.getOrDefault(movieCastColumn, "");
    }
}
