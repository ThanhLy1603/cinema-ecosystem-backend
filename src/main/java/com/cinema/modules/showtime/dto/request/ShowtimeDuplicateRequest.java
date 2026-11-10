package com.cinema.modules.showtime.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ShowtimeDuplicateRequest(
        @NotNull(message = "Vui lòng chọn suất chiếu nguồn")
        Long sourceShowtimeId,

        @NotEmpty(message = "Vui lòng chọn ít nhất một phòng chiếu")
        @Size(max = 20, message = "Tối đa 20 phòng chiếu mỗi lượt")
        List<@NotNull(message = "Phòng chiếu không hợp lệ") Long> hallIds,

        @NotEmpty(message = "Vui lòng chọn ít nhất một ngày")
        @Size(max = 62, message = "Tối đa 62 ngày mỗi lượt")
        List<@NotNull(message = "Ngày không hợp lệ") LocalDate> dates,

        boolean dryRun) {
}
