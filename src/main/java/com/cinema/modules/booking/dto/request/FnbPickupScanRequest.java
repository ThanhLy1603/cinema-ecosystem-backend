package com.cinema.modules.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FnbPickupScanRequest(
        @NotBlank(message = "Vui lòng nhập hoặc quét mã vé") @Size(max = 100, message = "Mã vé quá dài") String code,
        @NotNull(message = "Chưa chọn chi nhánh làm việc") Long cinemaId) {
}
