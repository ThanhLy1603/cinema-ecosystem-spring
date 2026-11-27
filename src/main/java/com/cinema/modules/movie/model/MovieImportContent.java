package com.cinema.modules.movie.model;

import java.util.List;

public record MovieImportContent(
        List<MovieImportSheetRow> movieRows, List<MovieImportCastRow> castRows) {
}
