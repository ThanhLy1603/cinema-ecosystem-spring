package com.cinema.modules.movie.model;

import java.util.List;

public record ExportSheet(String name, List<ExportColumn> columns, List<List<Object>> rows) {
}
