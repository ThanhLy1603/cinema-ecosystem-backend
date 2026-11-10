package com.cinema.modules.showtime.service;

import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.showtime.entity.PriceRule;
import com.cinema.modules.showtime.enums.PriceDayType;
import com.cinema.modules.showtime.enums.PriceRuleType;
import com.cinema.modules.showtime.model.ShowtimeSurchargeBreakdown;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicPricingCalculatorTest {

    private static final Instant SATURDAY_EVENING = Instant.parse("2026-11-14T12:00:00Z");
    private static final Instant TUESDAY_EVENING = Instant.parse("2026-11-10T12:00:00Z");

    private final DynamicPricingCalculator dynamicPricingCalculator = new DynamicPricingCalculator();

    private PriceRule dayRule(Long id, String name, PriceDayType priceDayType, String amount) {
        PriceRule priceRule = new PriceRule();
        priceRule.setId(id);
        priceRule.setRuleName(name);
        priceRule.setDayType(priceDayType);
        priceRule.setSurchargeAmount(new BigDecimal(amount));
        return priceRule;
    }

    private PriceRule timeRule(Long id, String name, String timeFrom, String timeTo, String amount) {
        PriceRule priceRule = dayRule(id, name, PriceDayType.ALL, amount);
        priceRule.setTimeFrom(LocalTime.parse(timeFrom));
        priceRule.setTimeTo(LocalTime.parse(timeTo));
        return priceRule;
    }

    private PriceRule screenRule(Long id, String name, ScreenType screenType, String amount) {
        PriceRule priceRule = dayRule(id, name, PriceDayType.ALL, amount);
        priceRule.setScreenType(screenType);
        return priceRule;
    }

    private PriceRule holidayRule(Long id, String name, String validFrom, String validTo, String amount) {
        PriceRule priceRule = dayRule(id, name, PriceDayType.HOLIDAY, amount);
        priceRule.setValidFrom(LocalDate.parse(validFrom));
        priceRule.setValidTo(LocalDate.parse(validTo));
        return priceRule;
    }

    private ShowtimeSurchargeBreakdown calculate(List<PriceRule> priceRules, ScreenType screenType, Instant startTime) {
        return dynamicPricingCalculator.calculate(priceRules, screenType, startTime);
    }

    @Test
    void ruleTypeIsDerivedFromTheDimensionTheRuleUses() {
        assertEquals(PriceRuleType.DAY_TYPE, dayRule(1L, "Cuối tuần", PriceDayType.WEEKEND, "15000").resolveRuleType());
        assertEquals(PriceRuleType.TIME_SLOT, timeRule(2L, "Đêm", "22:00", "04:00", "10000").resolveRuleType());
        assertEquals(PriceRuleType.SCREEN_TYPE, screenRule(3L, "IMAX", ScreenType.IMAX, "30000").resolveRuleType());
    }

    @Test
    void weekendRuleAppliesOnSaturdayAndSundayInVietnamTime() {
        List<PriceRule> priceRules = List.of(dayRule(1L, "Cuối tuần", PriceDayType.WEEKEND, "15000"));

        ShowtimeSurchargeBreakdown saturday = calculate(priceRules, ScreenType.SCREEN_2D, SATURDAY_EVENING);
        ShowtimeSurchargeBreakdown sunday = calculate(priceRules, ScreenType.SCREEN_2D, Instant.parse("2026-11-15T03:00:00Z"));
        ShowtimeSurchargeBreakdown tuesday = calculate(priceRules, ScreenType.SCREEN_2D, TUESDAY_EVENING);

        assertEquals(0, new BigDecimal("15000").compareTo(saturday.dayTypeSurcharge()));
        assertEquals(0, new BigDecimal("15000").compareTo(sunday.dayTypeSurcharge()));
        assertEquals(0, BigDecimal.ZERO.compareTo(tuesday.dayTypeSurcharge()));
        assertTrue(tuesday.appliedRules().isEmpty());
    }

    @Test
    void dayTypeIsDecidedByVietnamDateNotUtcDate() {
        List<PriceRule> priceRules = List.of(dayRule(1L, "Cuối tuần", PriceDayType.WEEKEND, "15000"));

        ShowtimeSurchargeBreakdown fridayNightUtcButSaturdayInVietnam =
                calculate(priceRules, ScreenType.SCREEN_2D, Instant.parse("2026-11-13T18:00:00Z"));

        assertEquals(0, new BigDecimal("15000").compareTo(fridayNightUtcButSaturdayInVietnam.dayTypeSurcharge()));
    }

    @Test
    void weekdayRuleDoesNotApplyOnWeekend() {
        List<PriceRule> priceRules = List.of(dayRule(1L, "Ngày thường", PriceDayType.WEEKDAY, "5000"));

        assertEquals(0, new BigDecimal("5000").compareTo(
                calculate(priceRules, ScreenType.SCREEN_2D, TUESDAY_EVENING).dayTypeSurcharge()));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                calculate(priceRules, ScreenType.SCREEN_2D, SATURDAY_EVENING).dayTypeSurcharge()));
    }

    @Test
    void holidayRuleReplacesWeekendRuleInsteadOfStackingOnTopOfIt() {
        List<PriceRule> priceRules = List.of(
                dayRule(1L, "Cuối tuần", PriceDayType.WEEKEND, "15000"),
                holidayRule(2L, "Lễ Quốc khánh", "2026-11-14", "2026-11-15", "30000"));

        ShowtimeSurchargeBreakdown holidayWeekend = calculate(priceRules, ScreenType.SCREEN_2D, SATURDAY_EVENING);

        assertEquals(0, new BigDecimal("30000").compareTo(holidayWeekend.dayTypeSurcharge()));
        assertEquals(1, holidayWeekend.appliedRules().size());
        assertEquals("Lễ Quốc khánh", holidayWeekend.appliedRules().get(0).ruleName());
    }

    @Test
    void holidayRuleOnlyAppliesInsideItsDateRange() {
        List<PriceRule> priceRules = List.of(holidayRule(2L, "Lễ 2/9", "2026-09-01", "2026-09-03", "30000"));

        assertEquals(0, new BigDecimal("30000").compareTo(
                calculate(priceRules, ScreenType.SCREEN_2D, Instant.parse("2026-09-02T12:00:00Z")).dayTypeSurcharge()));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                calculate(priceRules, ScreenType.SCREEN_2D, Instant.parse("2026-09-04T12:00:00Z")).dayTypeSurcharge()));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                calculate(priceRules, ScreenType.SCREEN_2D, Instant.parse("2026-08-31T12:00:00Z")).dayTypeSurcharge()));
    }

    @Test
    void nightSlotCrossingMidnightMatchesFromTenPmUntilFourAmExclusive() {
        List<PriceRule> priceRules = List.of(timeRule(1L, "Suất đêm", "22:00", "04:00", "10000"));

        assertEquals(0, BigDecimal.ZERO.compareTo(
                calculate(priceRules, ScreenType.SCREEN_2D, Instant.parse("2026-11-10T14:59:00Z")).timeSlotSurcharge()));
        assertEquals(0, new BigDecimal("10000").compareTo(
                calculate(priceRules, ScreenType.SCREEN_2D, Instant.parse("2026-11-10T15:00:00Z")).timeSlotSurcharge()));
        assertEquals(0, new BigDecimal("10000").compareTo(
                calculate(priceRules, ScreenType.SCREEN_2D, Instant.parse("2026-11-10T18:30:00Z")).timeSlotSurcharge()));
        assertEquals(0, new BigDecimal("10000").compareTo(
                calculate(priceRules, ScreenType.SCREEN_2D, Instant.parse("2026-11-10T20:59:00Z")).timeSlotSurcharge()));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                calculate(priceRules, ScreenType.SCREEN_2D, Instant.parse("2026-11-10T21:00:00Z")).timeSlotSurcharge()));
    }

    @Test
    void sameDaySlotIncludesStartAndExcludesEnd() {
        List<PriceRule> priceRules = List.of(timeRule(1L, "Giờ vàng", "18:00", "21:00", "8000"));

        assertEquals(0, new BigDecimal("8000").compareTo(
                calculate(priceRules, ScreenType.SCREEN_2D, Instant.parse("2026-11-10T11:00:00Z")).timeSlotSurcharge()));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                calculate(priceRules, ScreenType.SCREEN_2D, Instant.parse("2026-11-10T14:00:00Z")).timeSlotSurcharge()));
    }

    @Test
    void screenTypeRuleOnlyAppliesToThatScreenType() {
        List<PriceRule> priceRules = List.of(screenRule(1L, "Phụ thu IMAX", ScreenType.IMAX, "30000"));

        assertEquals(0, new BigDecimal("30000").compareTo(
                calculate(priceRules, ScreenType.IMAX, TUESDAY_EVENING).screenTypeSurcharge()));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                calculate(priceRules, ScreenType.SCREEN_2D, TUESDAY_EVENING).screenTypeSurcharge()));
    }

    @Test
    void validityWindowAppliesToTimeAndScreenRulesToo() {
        PriceRule seasonalImax = screenRule(1L, "IMAX mùa hè", ScreenType.IMAX, "30000");
        seasonalImax.setValidFrom(LocalDate.parse("2026-06-01"));
        seasonalImax.setValidTo(LocalDate.parse("2026-08-31"));

        assertEquals(0, BigDecimal.ZERO.compareTo(
                calculate(List.of(seasonalImax), ScreenType.IMAX, TUESDAY_EVENING).screenTypeSurcharge()));
    }

    @Test
    void multipleRulesInTheSameCategoryAreSummed() {
        List<PriceRule> priceRules = List.of(
                timeRule(1L, "Giờ vàng", "18:00", "21:00", "8000"),
                timeRule(2L, "Tối muộn", "19:00", "23:00", "4000"));

        ShowtimeSurchargeBreakdown breakdown = calculate(priceRules, ScreenType.SCREEN_2D, TUESDAY_EVENING);

        assertEquals(0, new BigDecimal("12000").compareTo(breakdown.timeSlotSurcharge()));
        assertEquals(2, breakdown.appliedRules().size());
    }

    @Test
    void breakdownSeparatesEveryCategoryAndTotalsThem() {
        List<PriceRule> priceRules = List.of(
                dayRule(1L, "Cuối tuần", PriceDayType.WEEKEND, "15000"),
                timeRule(2L, "Suất đêm", "22:00", "04:00", "10000"),
                screenRule(3L, "Phụ thu IMAX", ScreenType.IMAX, "30000"));

        ShowtimeSurchargeBreakdown breakdown =
                calculate(priceRules, ScreenType.IMAX, Instant.parse("2026-11-14T16:00:00Z"));

        assertEquals(0, new BigDecimal("10000").compareTo(breakdown.timeSlotSurcharge()));
        assertEquals(0, new BigDecimal("15000").compareTo(breakdown.dayTypeSurcharge()));
        assertEquals(0, new BigDecimal("30000").compareTo(breakdown.screenTypeSurcharge()));
        assertEquals(0, new BigDecimal("55000").compareTo(breakdown.total()));
        assertEquals(3, breakdown.appliedRules().size());
    }

    @Test
    void zeroAmountRulesAreNotReportedAsApplied() {
        List<PriceRule> priceRules = List.of(dayRule(1L, "Cuối tuần", PriceDayType.WEEKEND, "0"));

        assertTrue(calculate(priceRules, ScreenType.SCREEN_2D, SATURDAY_EVENING).appliedRules().isEmpty());
    }
}
