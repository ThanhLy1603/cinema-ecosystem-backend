package com.cinema.modules.booking.dto.response;

import com.cinema.modules.booking.enums.SalesChannel;
import com.cinema.modules.payment.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingExceptionResponse(
        BookingDetailResponse booking,
        SalesChannel salesChannel,
        PaymentMethod paymentMethod,
        int checkedInTicketCount,
        boolean canRefund,
        String refundBlockedReason,
        boolean canReissue,
        String reissueBlockedReason,
        BigDecimal refundedAmount,
        String refundReason,
        Instant refundedAt) {
}
