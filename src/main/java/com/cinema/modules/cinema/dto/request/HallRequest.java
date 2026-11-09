package com.cinema.modules.cinema.dto.request;

import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HallRequest(
        @NotBlank(message = "Vui lòng nhập tên phòng chiếu")
        @Size(max = 100, message = "Tên phòng chiếu tối đa 100 ký tự")
        String name,

        @NotNull(message = "Vui lòng chọn loại màn hình")
        ScreenType screenType,

        @NotNull(message = "Vui lòng chọn trạng thái")
        HallStatus status) {
}
