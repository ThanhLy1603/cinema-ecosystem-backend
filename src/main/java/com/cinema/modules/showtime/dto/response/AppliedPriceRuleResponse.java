package com.cinema.modules.showtime.dto.response;

import com.cinema.modules.showtime.enums.PriceRuleType;

import java.math.BigDecimal;

public record AppliedPriceRuleResponse(
        Long ruleId, String ruleName, PriceRuleType ruleType, BigDecimal surchargeAmount) {
}
