package com.cinema.modules.payment.dto.request;

import com.cinema.modules.payment.enums.SimulatedPaymentOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SimulatePaymentRequest(
        @NotBlank(message = "Thiếu mã giao dịch")
        @Size(max = 100, message = "Mã giao dịch không hợp lệ")
        String transactionRef,

        @NotNull(message = "Vui lòng chọn kết quả thanh toán")
        SimulatedPaymentOutcome outcome) {
}
