package com.cinema.modules.showtime.model;

import com.cinema.modules.showtime.dto.response.AppliedPriceRuleResponse;

import java.math.BigDecimal;
import java.util.List;

public record ShowtimeSurchargeBreakdown(
        BigDecimal timeSlotSurcharge,
        BigDecimal dayTypeSurcharge,
        BigDecimal screenTypeSurcharge,
        List<AppliedPriceRuleResponse> appliedRules) {

    public BigDecimal total() {
        return timeSlotSurcharge.add(dayTypeSurcharge).add(screenTypeSurcharge);
    }
}
