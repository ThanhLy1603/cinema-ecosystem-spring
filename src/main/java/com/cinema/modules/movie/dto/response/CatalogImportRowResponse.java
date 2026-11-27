package com.cinema.modules.movie.dto.response;

import com.cinema.modules.movie.enums.ImportRowStatus;

import java.util.List;

public record CatalogImportRowResponse(
        int rowNumber, String name, String detail, ImportRowStatus rowStatus, List<String> errors) {
}
