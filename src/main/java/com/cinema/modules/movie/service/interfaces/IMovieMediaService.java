package com.cinema.modules.movie.service.interfaces;

import com.cinema.modules.movie.dto.request.MovieMediaRequest;
import com.cinema.modules.movie.dto.response.MovieMediaTargetResponse;

import java.util.List;

public interface IMovieMediaService {

    List<MovieMediaTargetResponse> findMediaTargets();

    MovieMediaTargetResponse updateMedia(Long movieId, MovieMediaRequest movieMediaRequest);
}
