package com.cinema.modules.movie.dto.response;

import java.util.List;

public record MovieStatusBulkResponse(int updatedCount, List<MovieStatusFailureResponse> failures) {
}
