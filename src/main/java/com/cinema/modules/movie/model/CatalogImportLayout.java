package com.cinema.modules.movie.model;

import java.util.List;

public record CatalogImportLayout(
        String sheetName,
        List<CatalogImportColumn> columns,
        List<List<String>> sampleRows,
        String existingHeader,
        List<String> guideLines,
        String templateFilename) {
}
