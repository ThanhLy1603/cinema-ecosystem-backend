package com.cinema.modules.booking.dto.request;

import com.cinema.modules.booking.BookingRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BookingFnbRequest(
        @NotNull(message = "Danh sách bắp nước không hợp lệ")
        @Size(max = BookingRules.MAX_FNB_LINES_PER_BOOKING, message = "Chọn quá nhiều món trong một đơn")
        List<@NotNull(message = "Dòng bắp nước không hợp lệ") @Valid BookingFnbLineRequest> lines) {
}
