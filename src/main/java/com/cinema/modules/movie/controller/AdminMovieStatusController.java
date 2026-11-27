package com.cinema.modules.movie.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.movie.dto.request.MovieStatusBulkRequest;
import com.cinema.modules.movie.dto.response.MovieStatusBulkResponse;
import com.cinema.modules.movie.service.interfaces.IMovieBulkStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/movies")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
@RequiredArgsConstructor
public class AdminMovieStatusController {

    private final IMovieBulkStatusService movieBulkStatusService;

    @PatchMapping("/status")
    public ResponseEntity<ApiResponse<MovieStatusBulkResponse>> changeStatuses(
            @Valid @RequestBody MovieStatusBulkRequest movieStatusBulkRequest) {
        MovieStatusBulkResponse movieStatusBulkResponse =
                movieBulkStatusService.changeStatuses(movieStatusBulkRequest);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã đổi trạng thái " + movieStatusBulkResponse.updatedCount() + " phim", movieStatusBulkResponse));
    }
}
