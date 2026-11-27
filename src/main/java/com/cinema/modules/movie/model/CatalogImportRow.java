package com.cinema.modules.movie.model;

import java.util.List;

public record CatalogImportRow(int rowNumber, List<String> values) {

    public String value(int columnIndex) {
        return columnIndex < values.size() ? values.get(columnIndex) : "";
    }
}
