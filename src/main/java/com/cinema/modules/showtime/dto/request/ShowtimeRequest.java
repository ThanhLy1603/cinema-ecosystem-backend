package com.cinema.modules.showtime.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record ShowtimeRequest(
        @NotNull(message = "Vui lòng chọn phim")
        Long movieId,

        @NotNull(message = "Vui lòng chọn phòng chiếu")
        Long hallId,

        @NotNull(message = "Vui lòng chọn giờ bắt đầu")
        Instant startTime,

        @NotNull(message = "Vui lòng nhập giá vé gốc")
        @DecimalMin(value = "0", message = "Giá vé gốc không được âm")
        @DecimalMax(value = "10000000", message = "Giá vé gốc tối đa 10.000.000 VNĐ")
        @Digits(integer = 8, fraction = 2, message = "Giá vé gốc không hợp lệ")
        BigDecimal basePrice) {
}
