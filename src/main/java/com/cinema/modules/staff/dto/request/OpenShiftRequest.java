package com.cinema.modules.staff.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OpenShiftRequest(
        @NotNull(message = "Chưa chọn chi nhánh làm việc") Long cinemaId,
        @NotNull(message = "Vui lòng nhập tiền đầu ca")
        @DecimalMin(value = "0", message = "Tiền đầu ca không hợp lệ")
        @DecimalMax(value = "100000000", message = "Tiền đầu ca quá lớn")
        BigDecimal openingCash) {
}
