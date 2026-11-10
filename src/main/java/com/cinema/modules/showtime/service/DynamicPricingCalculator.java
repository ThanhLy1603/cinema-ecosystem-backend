package com.cinema.modules.showtime.service;

import com.cinema.common.util.BusinessZone;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.showtime.dto.response.AppliedPriceRuleResponse;
import com.cinema.modules.showtime.entity.PriceRule;
import com.cinema.modules.showtime.enums.PriceDayType;
import com.cinema.modules.showtime.enums.PriceRuleType;
import com.cinema.modules.showtime.model.ShowtimeSurchargeBreakdown;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class DynamicPricingCalculator {

    public ShowtimeSurchargeBreakdown calculate(List<PriceRule> priceRules, ScreenType screenType, Instant startTime) {
        ZonedDateTime localStartTime = startTime.atZone(BusinessZone.VIETNAM);
        LocalDate showDate = localStartTime.toLocalDate();
        LocalTime showTime = localStartTime.toLocalTime();
        List<PriceRule> matchedPriceRules = priceRules.stream()
                .filter(priceRule -> matches(priceRule, screenType, showDate, showTime))
                .toList();
        List<PriceRule> screenTypeRules = filterByType(matchedPriceRules, PriceRuleType.SCREEN_TYPE);
        List<PriceRule> timeSlotRules = filterByType(matchedPriceRules, PriceRuleType.TIME_SLOT);
        List<PriceRule> dayTypeRules = resolveDayTypeRules(filterByType(matchedPriceRules, PriceRuleType.DAY_TYPE));

        List<AppliedPriceRuleResponse> appliedRules = new ArrayList<>();
        appliedRules.addAll(toAppliedRules(timeSlotRules));
        appliedRules.addAll(toAppliedRules(dayTypeRules));
        appliedRules.addAll(toAppliedRules(screenTypeRules));
        return new ShowtimeSurchargeBreakdown(
                sum(timeSlotRules), sum(dayTypeRules), sum(screenTypeRules), List.copyOf(appliedRules));
    }

    boolean matches(PriceRule priceRule, ScreenType screenType, LocalDate showDate, LocalTime showTime) {
        return isWithinValidity(priceRule, showDate)
                && matchesDayType(priceRule.getDayType(), showDate)
                && matchesTimeWindow(priceRule.getTimeFrom(), priceRule.getTimeTo(), showTime)
                && (priceRule.getScreenType() == null || priceRule.getScreenType() == screenType);
    }

    private boolean isWithinValidity(PriceRule priceRule, LocalDate showDate) {
        boolean startedAlready = priceRule.getValidFrom() == null || !showDate.isBefore(priceRule.getValidFrom());
        boolean notExpired = priceRule.getValidTo() == null || !showDate.isAfter(priceRule.getValidTo());
        return startedAlready && notExpired;
    }

    private boolean matchesDayType(PriceDayType priceDayType, LocalDate showDate) {
        boolean weekend = showDate.getDayOfWeek() == DayOfWeek.SATURDAY || showDate.getDayOfWeek() == DayOfWeek.SUNDAY;
        return switch (priceDayType) {
            case ALL, HOLIDAY -> true;
            case WEEKEND -> weekend;
            case WEEKDAY -> !weekend;
        };
    }

    private boolean matchesTimeWindow(LocalTime timeFrom, LocalTime timeTo, LocalTime showTime) {
        if (timeFrom == null || timeTo == null) {
            return true;
        }
        if (timeFrom.isBefore(timeTo)) {
            return !showTime.isBefore(timeFrom) && showTime.isBefore(timeTo);
        }
        return !showTime.isBefore(timeFrom) || showTime.isBefore(timeTo);
    }

    private List<PriceRule> resolveDayTypeRules(List<PriceRule> dayTypeRules) {
        List<PriceRule> holidayRules = dayTypeRules.stream()
                .filter(priceRule -> priceRule.getDayType() == PriceDayType.HOLIDAY)
                .toList();
        return holidayRules.isEmpty() ? dayTypeRules : holidayRules;
    }

    private List<PriceRule> filterByType(List<PriceRule> priceRules, PriceRuleType priceRuleType) {
        return priceRules.stream().filter(priceRule -> priceRule.resolveRuleType() == priceRuleType).toList();
    }

    private BigDecimal sum(List<PriceRule> priceRules) {
        return priceRules.stream()
                .map(PriceRule::getSurchargeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<AppliedPriceRuleResponse> toAppliedRules(List<PriceRule> priceRules) {
        return priceRules.stream()
                .filter(priceRule -> priceRule.getSurchargeAmount().signum() > 0)
                .sorted(Comparator.comparing(PriceRule::getRuleName))
                .map(priceRule -> new AppliedPriceRuleResponse(
                        priceRule.getId(),
                        priceRule.getRuleName(),
                        priceRule.resolveRuleType(),
                        priceRule.getSurchargeAmount()))
                .toList();
    }
}
