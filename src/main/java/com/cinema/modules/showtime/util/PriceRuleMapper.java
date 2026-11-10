package com.cinema.modules.showtime.util;

import com.cinema.modules.showtime.dto.response.PriceRuleResponse;
import com.cinema.modules.showtime.entity.PriceRule;

public final class PriceRuleMapper {

    private PriceRuleMapper() {
    }

    public static PriceRuleResponse toPriceRuleResponse(PriceRule priceRule) {
        return new PriceRuleResponse(
                priceRule.getId(),
                priceRule.getRuleName(),
                priceRule.resolveRuleType(),
                priceRule.getDayType(),
                priceRule.getTimeFrom(),
                priceRule.getTimeTo(),
                priceRule.getScreenType(),
                priceRule.getValidFrom(),
                priceRule.getValidTo(),
                priceRule.getSurchargeAmount(),
                priceRule.isDeleted());
    }
}
