package com.cinema.modules.movie.service.interfaces;

import com.cinema.modules.movie.dto.response.MovieImportReportResponse;

import java.util.Collection;

public interface IMovieImportService {

    byte[] buildTemplate();

    MovieImportReportResponse previewImport(byte[] content);

    MovieImportReportResponse importMovies(byte[] content);

    byte[] exportRows(byte[] content, Collection<Integer> rowNumbers);
}
