package com.cinema.modules.movie.model;

import com.cinema.modules.movie.dto.request.MovieRequest;

import java.util.List;

public record ParsedMovieImportRow(MovieRequest movieRequest, List<String> genreNames, List<String> errors) {
}
