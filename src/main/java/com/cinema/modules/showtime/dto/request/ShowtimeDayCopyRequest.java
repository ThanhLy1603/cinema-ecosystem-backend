package com.cinema.modules.showtime.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ShowtimeDayCopyRequest(
        @NotNull(message = "Vui lòng chọn cụm rạp")
        Long cinemaId,

        @NotNull(message = "Vui lòng chọn ngày nguồn")
        LocalDate sourceDate,

        @NotEmpty(message = "Vui lòng chọn ít nhất một ngày đích")
        @Size(max = 31, message = "Tối đa 31 ngày đích mỗi lượt")
        List<@NotNull(message = "Ngày không hợp lệ") LocalDate> targetDates,

        boolean dryRun) {
}
