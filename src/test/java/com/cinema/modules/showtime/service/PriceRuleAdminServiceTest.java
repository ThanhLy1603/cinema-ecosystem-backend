package com.cinema.modules.showtime.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.showtime.dto.request.PriceRuleRequest;
import com.cinema.modules.showtime.dto.response.PriceRuleResponse;
import com.cinema.modules.showtime.entity.PriceRule;
import com.cinema.modules.showtime.enums.PriceDayType;
import com.cinema.modules.showtime.enums.PriceRuleType;
import com.cinema.modules.showtime.repository.PriceRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceRuleAdminServiceTest {

    private static final Long RULE_ID = 12L;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private PriceRuleRepository priceRuleRepository;

    private PriceRuleAdminService priceRuleAdminService;

    @BeforeEach
    void setUp() {
        priceRuleAdminService = new PriceRuleAdminService(priceRuleRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PriceRuleRequest request(
            PriceRuleType ruleType,
            PriceDayType dayType,
            String timeFrom,
            String timeTo,
            ScreenType screenType,
            LocalDate validFrom,
            LocalDate validTo) {
        return new PriceRuleRequest(
                "  Quy tắc thử  ",
                ruleType,
                new BigDecimal("15000"),
                dayType,
                timeFrom == null ? null : LocalTime.parse(timeFrom),
                timeTo == null ? null : LocalTime.parse(timeTo),
                screenType,
                validFrom,
                validTo);
    }

    private PriceRule saved() {
        ArgumentCaptor<PriceRule> priceRuleCaptor = ArgumentCaptor.forClass(PriceRule.class);
        verify(priceRuleRepository).saveAndFlush(priceRuleCaptor.capture());
        return priceRuleCaptor.getValue();
    }

    private void givenSaveEchoesEntity() {
        when(priceRuleRepository.saveAndFlush(any(PriceRule.class))).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    @Test
    void createTrimsNameAndStoresOnlyTheTimeSlotDimension() {
        givenSaveEchoesEntity();

        PriceRuleResponse priceRuleResponse = priceRuleAdminService.createPriceRule(request(
                PriceRuleType.TIME_SLOT, PriceDayType.WEEKEND, "22:00", "04:00", ScreenType.IMAX, null, null));

        PriceRule priceRule = saved();
        assertEquals("Quy tắc thử", priceRule.getRuleName());
        assertEquals(PriceDayType.ALL, priceRule.getDayType());
        assertEquals(LocalTime.of(22, 0), priceRule.getTimeFrom());
        assertEquals(LocalTime.of(4, 0), priceRule.getTimeTo());
        assertNull(priceRule.getScreenType());
        assertEquals(PriceRuleType.TIME_SLOT, priceRuleResponse.ruleType());
    }

    @Test
    void createScreenTypeRuleClearsDayAndTimeFields() {
        givenSaveEchoesEntity();

        priceRuleAdminService.createPriceRule(request(
                PriceRuleType.SCREEN_TYPE, PriceDayType.WEEKEND, "22:00", "04:00", ScreenType.IMAX, null, null));

        PriceRule priceRule = saved();
        assertEquals(ScreenType.IMAX, priceRule.getScreenType());
        assertEquals(PriceDayType.ALL, priceRule.getDayType());
        assertNull(priceRule.getTimeFrom());
    }

    @Test
    void createDayTypeRuleKeepsDayTypeAndValidity() {
        givenSaveEchoesEntity();

        priceRuleAdminService.createPriceRule(request(
                PriceRuleType.DAY_TYPE, PriceDayType.HOLIDAY, null, null, null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3)));

        PriceRule priceRule = saved();
        assertEquals(PriceDayType.HOLIDAY, priceRule.getDayType());
        assertEquals(LocalDate.of(2026, 9, 1), priceRule.getValidFrom());
        assertEquals(LocalDate.of(2026, 9, 3), priceRule.getValidTo());
    }

    @Test
    void createRejectsHolidayWithoutDateRange() {
        AppException appException = assertThrows(AppException.class, () -> priceRuleAdminService.createPriceRule(
                request(PriceRuleType.DAY_TYPE, PriceDayType.HOLIDAY, null, null, null, null, null)));

        assertEquals("INVALID_PRICE_RULE", appException.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, appException.getStatus());
        verify(priceRuleRepository, never()).saveAndFlush(any(PriceRule.class));
    }

    @Test
    void createRejectsDayTypeRuleWithoutSpecificDayType() {
        AppException appException = assertThrows(AppException.class, () -> priceRuleAdminService.createPriceRule(
                request(PriceRuleType.DAY_TYPE, PriceDayType.ALL, null, null, null, null, null)));

        assertEquals("INVALID_PRICE_RULE", appException.getErrorCode());
    }

    @Test
    void createRejectsTimeSlotWithMissingOrEqualBounds() {
        AppException missing = assertThrows(AppException.class, () -> priceRuleAdminService.createPriceRule(
                request(PriceRuleType.TIME_SLOT, null, "22:00", null, null, null, null)));
        AppException equal = assertThrows(AppException.class, () -> priceRuleAdminService.createPriceRule(
                request(PriceRuleType.TIME_SLOT, null, "22:00", "22:00", null, null, null)));

        assertEquals("INVALID_PRICE_RULE", missing.getErrorCode());
        assertEquals("INVALID_PRICE_RULE", equal.getErrorCode());
    }

    @Test
    void createRejectsScreenTypeRuleWithoutScreenType() {
        AppException appException = assertThrows(AppException.class, () -> priceRuleAdminService.createPriceRule(
                request(PriceRuleType.SCREEN_TYPE, null, null, null, null, null, null)));

        assertEquals("INVALID_PRICE_RULE", appException.getErrorCode());
    }

    @Test
    void createRejectsValidityEndingBeforeItStarts() {
        AppException appException = assertThrows(AppException.class, () -> priceRuleAdminService.createPriceRule(
                request(PriceRuleType.SCREEN_TYPE, null, null, null, ScreenType.IMAX,
                        LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 1))));

        assertEquals("INVALID_PRICE_RULE", appException.getErrorCode());
    }

    @Test
    void createRejectsDuplicateActiveName() {
        when(priceRuleRepository.existsByRuleNameIgnoreCaseAndDeletedFalse("Quy tắc thử")).thenReturn(true);

        AppException appException = assertThrows(AppException.class, () -> priceRuleAdminService.createPriceRule(
                request(PriceRuleType.SCREEN_TYPE, null, null, null, ScreenType.IMAX, null, null)));

        assertEquals("PRICE_RULE_NAME_EXISTS", appException.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, appException.getStatus());
    }

    @Test
    void createMapsConcurrentDuplicateNameToConflict() {
        when(priceRuleRepository.saveAndFlush(any(PriceRule.class))).thenThrow(new DataIntegrityViolationException("dup"));

        AppException appException = assertThrows(AppException.class, () -> priceRuleAdminService.createPriceRule(
                request(PriceRuleType.SCREEN_TYPE, null, null, null, ScreenType.IMAX, null, null)));

        assertEquals("PRICE_RULE_NAME_EXISTS", appException.getErrorCode());
    }

    @Test
    void updateSwitchesRuleTypeAndResetsTheOldDimension() {
        PriceRule existing = new PriceRule();
        existing.setId(RULE_ID);
        existing.setRuleName("Cũ");
        existing.setScreenType(ScreenType.IMAX);
        when(priceRuleRepository.findById(RULE_ID)).thenReturn(Optional.of(existing));
        givenSaveEchoesEntity();

        priceRuleAdminService.updatePriceRule(RULE_ID, request(
                PriceRuleType.TIME_SLOT, null, "18:00", "21:00", null, null, null));

        assertNull(existing.getScreenType());
        assertEquals(LocalTime.of(18, 0), existing.getTimeFrom());
        assertEquals(PriceRuleType.TIME_SLOT, existing.resolveRuleType());
    }

    @Test
    void updateIgnoresDeletedRules() {
        PriceRule deleted = new PriceRule();
        deleted.markDeleted(NOW);
        when(priceRuleRepository.findById(RULE_ID)).thenReturn(Optional.of(deleted));

        AppException appException = assertThrows(AppException.class, () -> priceRuleAdminService.updatePriceRule(
                RULE_ID, request(PriceRuleType.SCREEN_TYPE, null, null, null, ScreenType.IMAX, null, null)));

        assertEquals("PRICE_RULE_NOT_FOUND", appException.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, appException.getStatus());
    }

    @Test
    void deleteMarksRuleDeletedAtClockTime() {
        PriceRule existing = new PriceRule();
        existing.setRuleName("Cũ");
        when(priceRuleRepository.findById(RULE_ID)).thenReturn(Optional.of(existing));

        priceRuleAdminService.deletePriceRule(RULE_ID);

        assertTrue(existing.isDeleted());
        assertEquals(NOW, existing.getDeletedAt());
        verify(priceRuleRepository).save(existing);
    }

    @Test
    void restoreBringsBackADeletedRule() {
        PriceRule deleted = new PriceRule();
        deleted.setRuleName("Cũ");
        deleted.markDeleted(NOW);
        when(priceRuleRepository.findById(RULE_ID)).thenReturn(Optional.of(deleted));
        when(priceRuleRepository.existsByRuleNameIgnoreCaseAndDeletedFalse("Cũ")).thenReturn(false);
        givenSaveEchoesEntity();

        PriceRuleResponse priceRuleResponse = priceRuleAdminService.restorePriceRule(RULE_ID);

        assertFalse(deleted.isDeleted());
        assertFalse(priceRuleResponse.deleted());
    }

    @Test
    void restoreIsRefusedWhenAnActiveRuleTookTheName() {
        PriceRule deleted = new PriceRule();
        deleted.setRuleName("Cũ");
        deleted.markDeleted(NOW);
        when(priceRuleRepository.findById(RULE_ID)).thenReturn(Optional.of(deleted));
        when(priceRuleRepository.existsByRuleNameIgnoreCaseAndDeletedFalse("Cũ")).thenReturn(true);

        AppException appException = assertThrows(AppException.class, () -> priceRuleAdminService.restorePriceRule(RULE_ID));

        assertEquals("PRICE_RULE_NAME_EXISTS", appException.getErrorCode());
        assertTrue(deleted.isDeleted());
    }
}
