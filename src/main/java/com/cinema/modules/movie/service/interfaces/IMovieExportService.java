package com.cinema.modules.movie.service.interfaces;

import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;

public interface IMovieExportService {

    byte[] exportMovies(String keyword, Long genreId, MovieStatus status, AgeRating rated, boolean deleted);

    byte[] exportGenres(String keyword);

    byte[] exportPersons(String keyword);
}
