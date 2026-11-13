package com.cinema.modules.payment.dto.request;

import com.cinema.modules.booking.BookingRules;
import com.cinema.modules.booking.dto.request.BookingFnbLineRequest;
import com.cinema.modules.payment.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CounterFnbCheckoutRequest(
        @NotNull(message = "Chưa chọn chi nhánh làm việc") Long cinemaId,
        @NotNull(message = "Vui lòng chọn phương thức thanh toán") PaymentMethod paymentMethod,
        @DecimalMin(value = "0", message = "Số tiền khách đưa không hợp lệ") BigDecimal cashReceived,
        @NotEmpty(message = "Vui lòng chọn ít nhất một combo hoặc món")
        @Size(max = BookingRules.MAX_FNB_LINES_PER_BOOKING, message = "Chọn quá nhiều món trong một đơn")
        List<@NotNull(message = "Dòng bắp nước không hợp lệ") @Valid BookingFnbLineRequest> lines) {
}
