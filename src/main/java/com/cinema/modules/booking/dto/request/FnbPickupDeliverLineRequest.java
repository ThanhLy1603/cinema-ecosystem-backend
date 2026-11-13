package com.cinema.modules.booking.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FnbPickupDeliverLineRequest(
        @NotNull(message = "Thiếu dòng bắp nước cần giao") Long detailId,
        @Min(value = 1, message = "Số lượng giao phải từ 1") int quantity) {
}
