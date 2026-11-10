package com.cinema.modules.showtime.dto.response;

import com.cinema.modules.cinema.enums.ScreenType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ShowtimePriceResponse(
        Long showtimeId,
        ScreenType screenType,
        Instant startTime,
        BigDecimal basePrice,
        BigDecimal timeSlotSurcharge,
        BigDecimal dayTypeSurcharge,
        BigDecimal screenTypeSurcharge,
        List<AppliedPriceRuleResponse> appliedRules,
        List<SeatTypePriceResponse> seatTypePrices) {
}
