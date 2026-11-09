package com.cinema.modules.cinema.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FacilityRequest(
        @NotBlank(message = "Vui lòng nhập tên tiện ích")
        @Size(max = 100, message = "Tên tiện ích tối đa 100 ký tự")
        String name,

        @Size(max = 500, message = "Đường dẫn biểu tượng tối đa 500 ký tự")
        @Pattern(regexp = "^(https://\\S+)?$", message = "Biểu tượng phải là đường dẫn bắt đầu bằng https://")
        String iconUrl,

        @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
        String description) {
}
