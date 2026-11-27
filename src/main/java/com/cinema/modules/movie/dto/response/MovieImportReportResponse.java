package com.cinema.modules.movie.dto.response;

import com.cinema.modules.movie.enums.ImportRowStatus;

import java.util.List;

public record MovieImportReportResponse(
        int totalRows,
        int validRows,
        int duplicateRows,
        int errorRows,
        int createdRows,
        List<MovieImportRowResponse> rows,
        List<String> unmatchedCastErrors) {

    public static MovieImportReportResponse of(
            List<MovieImportRowResponse> movieImportRowResponses, List<String> unmatchedCastErrors) {
        return new MovieImportReportResponse(
                movieImportRowResponses.size(),
                count(movieImportRowResponses, ImportRowStatus.VALID),
                count(movieImportRowResponses, ImportRowStatus.DUPLICATE),
                count(movieImportRowResponses, ImportRowStatus.ERROR),
                count(movieImportRowResponses, ImportRowStatus.CREATED),
                movieImportRowResponses,
                unmatchedCastErrors);
    }

    private static int count(
            List<MovieImportRowResponse> movieImportRowResponses, ImportRowStatus importRowStatus) {
        return (int) movieImportRowResponses.stream()
                .filter(movieImportRowResponse -> movieImportRowResponse.rowStatus() == importRowStatus)
                .count();
    }
}
