package com.cinema.modules.movie.dto.response;

import com.cinema.modules.movie.enums.MovieStatus;

public record MovieMediaTargetResponse(
        Long id,
        String title,
        String slug,
        MovieStatus status,
        String posterUrl,
        String bannerUrl,
        String trailerUrl) {
}
