package com.cinema.modules.movie.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenreRequest(
        @NotBlank(message = "Vui lòng nhập tên thể loại")
        @Size(max = 100, message = "Tên thể loại tối đa 100 ký tự")
        String name) {
}
