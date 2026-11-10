package com.cinema.modules.showtime.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record SeatPriceResponse(
        Long showtimeId,
        Long seatId,
        String seatTypeCode,
        String seatTypeName,
        BigDecimal basePrice,
        BigDecimal seatSurcharge,
        BigDecimal timeSlotSurcharge,
        BigDecimal dayTypeSurcharge,
        BigDecimal screenTypeSurcharge,
        BigDecimal totalPrice,
        List<AppliedPriceRuleResponse> appliedRules) {
}
