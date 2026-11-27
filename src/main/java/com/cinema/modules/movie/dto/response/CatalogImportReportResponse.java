package com.cinema.modules.movie.dto.response;

import com.cinema.modules.movie.enums.ImportRowStatus;

import java.util.List;

public record CatalogImportReportResponse(
        int totalRows,
        int validRows,
        int duplicateRows,
        int errorRows,
        int createdRows,
        List<CatalogImportRowResponse> rows) {

    public static CatalogImportReportResponse of(List<CatalogImportRowResponse> catalogImportRowResponses) {
        return new CatalogImportReportResponse(
                catalogImportRowResponses.size(),
                count(catalogImportRowResponses, ImportRowStatus.VALID),
                count(catalogImportRowResponses, ImportRowStatus.DUPLICATE),
                count(catalogImportRowResponses, ImportRowStatus.ERROR),
                count(catalogImportRowResponses, ImportRowStatus.CREATED),
                catalogImportRowResponses);
    }

    private static int count(
            List<CatalogImportRowResponse> catalogImportRowResponses, ImportRowStatus importRowStatus) {
        return (int) catalogImportRowResponses.stream()
                .filter(catalogImportRowResponse -> catalogImportRowResponse.rowStatus() == importRowStatus)
                .count();
    }
}
