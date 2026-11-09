package com.cinema.modules.cinema.dto.request;

import com.cinema.modules.cinema.enums.SeatStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SeatCellRequest(
        @NotBlank(message = "Hàng ghế không được để trống")
        @Pattern(regexp = "^[A-Z]$", message = "Hàng ghế phải là một chữ cái từ A đến Z")
        String row,

        @Min(value = 1, message = "Số ghế tối thiểu là 1")
        @Max(value = 40, message = "Số ghế tối đa là 40")
        int number,

        @NotBlank(message = "Loại ghế không được để trống")
        String seatTypeCode,

        @NotNull(message = "Trạng thái ghế không được để trống")
        SeatStatus status) {
}
