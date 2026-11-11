package com.cinema.modules.promotion.service.interfaces;

import java.math.BigDecimal;

public interface ILoyaltyService {

    int earnPointsForBooking(Long userId, Long bookingId, BigDecimal paidAmount);
}
