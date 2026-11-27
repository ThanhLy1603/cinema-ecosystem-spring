package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.movie.dto.request.MovieMediaRequest;
import com.cinema.modules.movie.entity.Movie;
import com.cinema.modules.movie.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieMediaServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private PublicMovieCache publicMovieCache;

    @InjectMocks
    private MovieMediaService movieMediaService;

    @Test
    void onlyTheProvidedFilesAreReplaced() {
        Movie movie = new Movie();
        movie.setPosterUrl("https://cdn/old-poster.jpg");
        movie.setTrailerUrl("https://cdn/old-trailer.mp4");
        when(movieRepository.findActiveByIdForUpdate(7L)).thenReturn(Optional.of(movie));
        when(movieRepository.save(movie)).thenReturn(movie);

        movieMediaService.updateMedia(7L, new MovieMediaRequest(null, "https://cdn/banner.jpg", ""));

        assertEquals("https://cdn/old-poster.jpg", movie.getPosterUrl());
        assertEquals("https://cdn/banner.jpg", movie.getBannerUrl());
        assertEquals("https://cdn/old-trailer.mp4", movie.getTrailerUrl());
        verify(publicMovieCache).evictAfterCommit();
    }

    @Test
    void requestWithoutAnyFileIsRejected() {
        AppException appException = assertThrows(
                AppException.class, () -> movieMediaService.updateMedia(7L, new MovieMediaRequest(null, null, null)));

        assertEquals("MOVIE_MEDIA_EMPTY", appException.getErrorCode());
    }
}
