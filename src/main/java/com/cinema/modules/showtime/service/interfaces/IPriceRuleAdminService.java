package com.cinema.modules.showtime.service.interfaces;

import com.cinema.modules.showtime.dto.request.PriceRuleRequest;
import com.cinema.modules.showtime.dto.response.PriceRuleResponse;

import java.util.List;

public interface IPriceRuleAdminService {

    List<PriceRuleResponse> listPriceRules(boolean deleted);

    PriceRuleResponse createPriceRule(PriceRuleRequest priceRuleRequest);

    PriceRuleResponse updatePriceRule(Long priceRuleId, PriceRuleRequest priceRuleRequest);

    void deletePriceRule(Long priceRuleId);

    PriceRuleResponse restorePriceRule(Long priceRuleId);
}
