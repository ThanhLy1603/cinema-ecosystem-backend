package com.cinema.modules.payment.service.interfaces;

import com.cinema.modules.payment.dto.response.RefundedPaymentResponse;

import java.util.Optional;

public interface IPaymentRefundService {

    Optional<RefundedPaymentResponse> markBookingPaymentRefunded(Long bookingId);
}
