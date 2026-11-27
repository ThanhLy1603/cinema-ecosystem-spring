package com.cinema.modules.movie.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.movie.dto.request.MovieMediaRequest;
import com.cinema.modules.movie.dto.response.MovieMediaTargetResponse;
import com.cinema.modules.movie.service.interfaces.IMovieMediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/movies")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
@RequiredArgsConstructor
public class AdminMovieMediaController {

    private final IMovieMediaService movieMediaService;

    @GetMapping("/media-targets")
    public ResponseEntity<ApiResponse<List<MovieMediaTargetResponse>>> findMediaTargets() {
        return ResponseEntity.ok(ApiResponse.success(movieMediaService.findMediaTargets()));
    }

    @PatchMapping("/{movieId}/media")
    public ResponseEntity<ApiResponse<MovieMediaTargetResponse>> updateMedia(
            @PathVariable Long movieId, @Valid @RequestBody MovieMediaRequest movieMediaRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật tệp của phim thành công", movieMediaService.updateMedia(movieId, movieMediaRequest)));
    }
}
