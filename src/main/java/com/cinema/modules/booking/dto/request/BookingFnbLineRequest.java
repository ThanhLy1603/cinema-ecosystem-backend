package com.cinema.modules.booking.dto.request;

import com.cinema.modules.booking.BookingRules;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BookingFnbLineRequest(
        Long comboId,

        Long itemId,

        @NotNull(message = "Vui lòng nhập số lượng")
        @Min(value = 1, message = "Số lượng tối thiểu 1")
        @Max(value = BookingRules.MAX_FNB_QUANTITY_PER_LINE, message = "Mỗi món tối đa 10")
        Integer quantity) {
}
