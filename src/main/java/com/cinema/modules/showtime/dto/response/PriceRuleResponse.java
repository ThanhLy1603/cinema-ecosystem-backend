package com.cinema.modules.showtime.dto.response;

import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.showtime.enums.PriceDayType;
import com.cinema.modules.showtime.enums.PriceRuleType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record PriceRuleResponse(
        Long id,
        String ruleName,
        PriceRuleType ruleType,
        PriceDayType dayType,
        LocalTime timeFrom,
        LocalTime timeTo,
        ScreenType screenType,
        LocalDate validFrom,
        LocalDate validTo,
        BigDecimal surchargeAmount,
        boolean deleted) {
}
