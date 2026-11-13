package com.cinema.modules.staff.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CloseShiftRequest(
        @NotNull(message = "Vui lòng nhập tiền mặt đếm được")
        @DecimalMin(value = "0", message = "Số tiền không hợp lệ")
        @DecimalMax(value = "1000000000", message = "Số tiền quá lớn")
        BigDecimal countedCash,
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) {
}
