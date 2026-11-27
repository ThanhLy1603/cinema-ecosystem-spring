package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.movie.dto.request.MovieStatusBulkRequest;
import com.cinema.modules.movie.dto.response.MovieStatusBulkResponse;
import com.cinema.modules.movie.dto.response.MovieStatusFailureResponse;
import com.cinema.modules.movie.entity.Movie;
import com.cinema.modules.movie.repository.MovieRepository;
import com.cinema.modules.movie.service.interfaces.IMovieAdminService;
import com.cinema.modules.movie.service.interfaces.IMovieBulkStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MovieBulkStatusService implements IMovieBulkStatusService {

    private final IMovieAdminService movieAdminService;
    private final MovieRepository movieRepository;

    @Override
    public MovieStatusBulkResponse changeStatuses(MovieStatusBulkRequest movieStatusBulkRequest) {
        int updatedCount = 0;
        List<MovieStatusFailureResponse> movieStatusFailureResponses = new ArrayList<>();
        for (Long movieId : new LinkedHashSet<>(movieStatusBulkRequest.movieIds())) {
            try {
                movieAdminService.changeStatus(movieId, movieStatusBulkRequest.status());
                updatedCount++;
            } catch (AppException appException) {
                String title = movieRepository.findById(movieId).map(Movie::getTitle).orElse("Phim " + movieId);
                movieStatusFailureResponses.add(
                        new MovieStatusFailureResponse(movieId, title, appException.getMessage()));
            }
        }
        return new MovieStatusBulkResponse(updatedCount, movieStatusFailureResponses);
    }
}
