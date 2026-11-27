package com.cinema.modules.movie.controller;

import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.service.interfaces.IMovieExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
@RequiredArgsConstructor
public class AdminMovieExportController {

    private final IMovieExportService movieExportService;

    @GetMapping("/movies/export")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER') and (hasAuthority('ROLE_ADMIN') or !#deleted)")
    public ResponseEntity<byte[]> exportMovies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long genreId,
            @RequestParam(required = false) MovieStatus status,
            @RequestParam(required = false) AgeRating rated,
            @RequestParam(defaultValue = "false") boolean deleted) {
        return ImportFiles.toXlsxResponse(
                movieExportService.exportMovies(keyword, genreId, status, rated, deleted), "danh-sach-phim.xlsx");
    }

    @GetMapping("/genres/export")
    public ResponseEntity<byte[]> exportGenres(@RequestParam(required = false) String keyword) {
        return ImportFiles.toXlsxResponse(movieExportService.exportGenres(keyword), "danh-sach-the-loai.xlsx");
    }

    @GetMapping("/persons/export")
    public ResponseEntity<byte[]> exportPersons(@RequestParam(required = false) String keyword) {
        return ImportFiles.toXlsxResponse(movieExportService.exportPersons(keyword), "danh-sach-nghe-si.xlsx");
    }
}
