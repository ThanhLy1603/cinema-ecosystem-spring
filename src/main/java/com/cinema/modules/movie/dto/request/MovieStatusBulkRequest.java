package com.cinema.modules.movie.dto.request;

import com.cinema.modules.movie.enums.MovieStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MovieStatusBulkRequest(
        @NotEmpty(message = "Vui lòng chọn ít nhất một phim")
        @Size(max = 100, message = "Mỗi lần đổi tối đa 100 phim")
        List<@NotNull(message = "Phim không hợp lệ") Long> movieIds,

        @NotNull(message = "Vui lòng chọn trạng thái")
        MovieStatus status) {
}
