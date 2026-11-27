package com.cinema.modules.movie.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MovieMediaRequest(
        @Size(max = 500, message = "Đường dẫn poster tối đa 500 ký tự")
        @Pattern(regexp = "^(https://\\S+)?$", message = "Poster phải là đường dẫn bắt đầu bằng https://")
        String posterUrl,

        @Size(max = 500, message = "Đường dẫn banner tối đa 500 ký tự")
        @Pattern(regexp = "^(https://\\S+)?$", message = "Banner phải là đường dẫn bắt đầu bằng https://")
        String bannerUrl,

        @Size(max = 500, message = "Đường dẫn trailer tối đa 500 ký tự")
        @Pattern(regexp = "^(https://\\S+)?$", message = "Trailer phải là đường dẫn bắt đầu bằng https://")
        String trailerUrl) {
}
