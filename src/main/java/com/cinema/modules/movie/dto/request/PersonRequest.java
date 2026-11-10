package com.cinema.modules.movie.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PersonRequest(
        @NotBlank(message = "Vui lòng nhập tên nghệ sĩ")
        @Size(max = 150, message = "Tên nghệ sĩ tối đa 150 ký tự")
        String name,

        @Size(max = 500, message = "Đường dẫn ảnh tối đa 500 ký tự")
        @Pattern(regexp = "^(https://\\S+)?$", message = "Ảnh phải là đường dẫn bắt đầu bằng https://")
        String avatarUrl,

        @Size(max = 2000, message = "Tiểu sử tối đa 2000 ký tự")
        String bio) {
}
