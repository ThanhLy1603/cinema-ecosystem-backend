package com.cinema.modules.payment.dto.request;

import com.cinema.modules.payment.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CounterCheckoutRequest(
        @NotNull(message = "Chưa chọn chi nhánh làm việc") Long cinemaId,
        @NotNull(message = "Vui lòng chọn phương thức thanh toán") PaymentMethod paymentMethod,
        @DecimalMin(value = "0", message = "Số tiền khách đưa không hợp lệ") BigDecimal cashReceived,
        Long customerUserId,
        @Email(message = "Email không hợp lệ") @Size(max = 150, message = "Email quá dài") String receiptEmail) {
}
