package com.cinema.modules.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookingExceptionActionRequest(
        @NotBlank(message = "Vui lòng nhập lý do") @Size(max = 500, message = "Lý do tối đa 500 ký tự") String reason) {
}
