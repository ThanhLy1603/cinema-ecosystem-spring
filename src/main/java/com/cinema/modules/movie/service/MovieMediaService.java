package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.movie.dto.request.MovieMediaRequest;
import com.cinema.modules.movie.dto.response.MovieMediaTargetResponse;
import com.cinema.modules.movie.entity.Movie;
import com.cinema.modules.movie.repository.MovieRepository;
import com.cinema.modules.movie.service.interfaces.IMovieMediaService;
import com.cinema.modules.movie.util.MovieMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MovieMediaService implements IMovieMediaService {

    private final MovieRepository movieRepository;
    private final PublicMovieCache publicMovieCache;

    @Transactional(readOnly = true)
    @Override
    public List<MovieMediaTargetResponse> findMediaTargets() {
        return movieRepository.findAllByDeletedFalseOrderByTitleAscIdAsc().stream()
                .map(MovieMapper::toMovieMediaTargetResponse)
                .toList();
    }

    @Transactional
    @Override
    public MovieMediaTargetResponse updateMedia(Long movieId, MovieMediaRequest movieMediaRequest) {
        if (isBlank(movieMediaRequest.posterUrl())
                && isBlank(movieMediaRequest.bannerUrl())
                && isBlank(movieMediaRequest.trailerUrl())) {
            throw new AppException("Chưa có tệp nào để cập nhật", HttpStatus.BAD_REQUEST, "MOVIE_MEDIA_EMPTY");
        }
        Movie movie = movieRepository.findActiveByIdForUpdate(movieId)
                .orElseThrow(MovieViewAssembler::movieNotFound);
        if (!isBlank(movieMediaRequest.posterUrl())) {
            movie.setPosterUrl(movieMediaRequest.posterUrl());
        }
        if (!isBlank(movieMediaRequest.bannerUrl())) {
            movie.setBannerUrl(movieMediaRequest.bannerUrl());
        }
        if (!isBlank(movieMediaRequest.trailerUrl())) {
            movie.setTrailerUrl(movieMediaRequest.trailerUrl());
        }
        publicMovieCache.evictAfterCommit();
        return MovieMapper.toMovieMediaTargetResponse(movieRepository.save(movie));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
