package com.cinema.modules.movie.service.interfaces;

import com.cinema.modules.movie.dto.request.MovieStatusBulkRequest;
import com.cinema.modules.movie.dto.response.MovieStatusBulkResponse;

public interface IMovieBulkStatusService {

    MovieStatusBulkResponse changeStatuses(MovieStatusBulkRequest movieStatusBulkRequest);
}
