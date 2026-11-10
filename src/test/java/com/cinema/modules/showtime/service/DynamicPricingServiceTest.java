package com.cinema.modules.showtime.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.dto.response.SeatPricingInfoResponse;
import com.cinema.modules.cinema.dto.response.SeatTypeSurchargeResponse;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.showtime.dto.request.PriceSimulationRequest;
import com.cinema.modules.showtime.dto.response.SeatPriceResponse;
import com.cinema.modules.showtime.dto.response.SeatTypePriceResponse;
import com.cinema.modules.showtime.dto.response.ShowtimePriceResponse;
import com.cinema.modules.showtime.entity.PriceRule;
import com.cinema.modules.showtime.entity.Showtime;
import com.cinema.modules.showtime.enums.PriceDayType;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import com.cinema.modules.showtime.repository.PriceRuleRepository;
import com.cinema.modules.showtime.repository.ShowtimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicPricingServiceTest {

    private static final Long SHOWTIME_ID = 70L;
    private static final Long HALL_ID = 3L;
    private static final Long SEAT_ID = 500L;
    private static final Instant SATURDAY_EVENING = Instant.parse("2026-11-14T12:00:00Z");

    @Mock
    private ShowtimeRepository showtimeRepository;

    @Mock
    private PriceRuleRepository priceRuleRepository;

    @Mock
    private ICinemaQueryService cinemaQueryService;

    private DynamicPricingService dynamicPricingService;
    private Showtime showtime;

    @BeforeEach
    void setUp() {
        dynamicPricingService = new DynamicPricingService(
                showtimeRepository, priceRuleRepository, new DynamicPricingCalculator(), cinemaQueryService);
        showtime = new Showtime();
        showtime.setId(SHOWTIME_ID);
        showtime.setMovieId(9L);
        showtime.setHallId(HALL_ID);
        showtime.setStartTime(SATURDAY_EVENING);
        showtime.setEndTime(SATURDAY_EVENING.plusSeconds(6300));
        showtime.setBasePrice(new BigDecimal("90000"));
        showtime.setStatus(ShowtimeStatus.ACTIVE);
    }

    private PriceRule weekendRule() {
        PriceRule priceRule = new PriceRule();
        priceRule.setId(1L);
        priceRule.setRuleName("Phụ thu cuối tuần");
        priceRule.setDayType(PriceDayType.WEEKEND);
        priceRule.setSurchargeAmount(new BigDecimal("15000"));
        return priceRule;
    }

    private void givenHall(ScreenType screenType) {
        when(cinemaQueryService.findHallInfos(List.of(HALL_ID))).thenReturn(Map.of(
                HALL_ID, new HallInfoResponse(HALL_ID, 1L, "Phòng 01", screenType, HallStatus.ACTIVE, false)));
    }

    private void givenSeatTypes() {
        when(cinemaQueryService.findSeatTypeSurcharges()).thenReturn(List.of(
                new SeatTypeSurchargeResponse("STANDARD", "Ghế thường", BigDecimal.ZERO),
                new SeatTypeSurchargeResponse("VIP", "Ghế VIP", new BigDecimal("20000")),
                new SeatTypeSurchargeResponse("SWEETBOX", "Ghế đôi Sweetbox", new BigDecimal("40000"))));
    }

    @Test
    void saturdayEveningVipSeatCostsBasePlusSeatPlusWeekend() {
        when(showtimeRepository.findById(SHOWTIME_ID)).thenReturn(Optional.of(showtime));
        givenHall(ScreenType.SCREEN_2D);
        when(priceRuleRepository.findByDeletedFalse()).thenReturn(List.of(weekendRule()));
        givenSeatTypes();

        ShowtimePriceResponse showtimePriceResponse = dynamicPricingService.getShowtimePrices(SHOWTIME_ID);

        assertEquals(0, new BigDecimal("90000").compareTo(showtimePriceResponse.basePrice()));
        assertEquals(0, new BigDecimal("15000").compareTo(showtimePriceResponse.dayTypeSurcharge()));
        SeatTypePriceResponse standard = showtimePriceResponse.seatTypePrices().get(0);
        SeatTypePriceResponse vip = showtimePriceResponse.seatTypePrices().get(1);
        SeatTypePriceResponse sweetbox = showtimePriceResponse.seatTypePrices().get(2);
        assertEquals(0, new BigDecimal("105000").compareTo(standard.totalPrice()));
        assertEquals(0, new BigDecimal("125000").compareTo(vip.totalPrice()));
        assertEquals(0, new BigDecimal("145000").compareTo(sweetbox.totalPrice()));
    }

    @Test
    void calculateSeatPriceReturnsEveryComponentOfTheFinalPrice() {
        when(showtimeRepository.findById(SHOWTIME_ID)).thenReturn(Optional.of(showtime));
        givenHall(ScreenType.SCREEN_2D);
        when(priceRuleRepository.findByDeletedFalse()).thenReturn(List.of(weekendRule()));
        when(cinemaQueryService.findSeatPricingInfos(List.of(SEAT_ID))).thenReturn(Map.of(
                SEAT_ID, new SeatPricingInfoResponse(SEAT_ID, HALL_ID, "VIP", "Ghế VIP", new BigDecimal("20000"), true)));

        SeatPriceResponse seatPriceResponse = dynamicPricingService.calculateSeatPrice(SHOWTIME_ID, SEAT_ID);

        assertEquals(0, new BigDecimal("90000").compareTo(seatPriceResponse.basePrice()));
        assertEquals(0, new BigDecimal("20000").compareTo(seatPriceResponse.seatSurcharge()));
        assertEquals(0, new BigDecimal("15000").compareTo(seatPriceResponse.dayTypeSurcharge()));
        assertEquals(0, BigDecimal.ZERO.compareTo(seatPriceResponse.timeSlotSurcharge()));
        assertEquals(0, BigDecimal.ZERO.compareTo(seatPriceResponse.screenTypeSurcharge()));
        assertEquals(0, new BigDecimal("125000").compareTo(seatPriceResponse.totalPrice()));
        assertEquals("VIP", seatPriceResponse.seatTypeCode());
        assertEquals(1, seatPriceResponse.appliedRules().size());
    }

    @Test
    void calculateSeatPriceRejectsSeatOfAnotherHall() {
        when(showtimeRepository.findById(SHOWTIME_ID)).thenReturn(Optional.of(showtime));
        givenHall(ScreenType.SCREEN_2D);
        when(cinemaQueryService.findSeatPricingInfos(List.of(SEAT_ID))).thenReturn(Map.of(
                SEAT_ID, new SeatPricingInfoResponse(SEAT_ID, 99L, "VIP", "Ghế VIP", new BigDecimal("20000"), true)));

        AppException appException = assertThrows(
                AppException.class, () -> dynamicPricingService.calculateSeatPrice(SHOWTIME_ID, SEAT_ID));

        assertEquals("SEAT_NOT_IN_SHOWTIME_HALL", appException.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, appException.getStatus());
    }

    @Test
    void calculateSeatPriceRejectsSeatUnderMaintenance() {
        when(showtimeRepository.findById(SHOWTIME_ID)).thenReturn(Optional.of(showtime));
        givenHall(ScreenType.SCREEN_2D);
        when(cinemaQueryService.findSeatPricingInfos(List.of(SEAT_ID))).thenReturn(Map.of(
                SEAT_ID, new SeatPricingInfoResponse(SEAT_ID, HALL_ID, "VIP", "Ghế VIP", new BigDecimal("20000"), false)));

        AppException appException = assertThrows(
                AppException.class, () -> dynamicPricingService.calculateSeatPrice(SHOWTIME_ID, SEAT_ID));

        assertEquals("SEAT_NOT_BOOKABLE", appException.getErrorCode());
    }

    @Test
    void calculateSeatPriceRejectsUnknownSeat() {
        when(showtimeRepository.findById(SHOWTIME_ID)).thenReturn(Optional.of(showtime));
        givenHall(ScreenType.SCREEN_2D);
        when(cinemaQueryService.findSeatPricingInfos(List.of(SEAT_ID))).thenReturn(Map.of());

        AppException appException = assertThrows(
                AppException.class, () -> dynamicPricingService.calculateSeatPrice(SHOWTIME_ID, SEAT_ID));

        assertEquals("SEAT_NOT_FOUND", appException.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, appException.getStatus());
    }

    @Test
    void cancelledShowtimeHasNoPublicPrice() {
        showtime.setStatus(ShowtimeStatus.CANCELLED);
        when(showtimeRepository.findById(SHOWTIME_ID)).thenReturn(Optional.of(showtime));

        AppException appException = assertThrows(
                AppException.class, () -> dynamicPricingService.getShowtimePrices(SHOWTIME_ID));

        assertEquals("SHOWTIME_NOT_FOUND", appException.getErrorCode());
    }

    @Test
    void unknownShowtimeIsNotFound() {
        when(showtimeRepository.findById(SHOWTIME_ID)).thenReturn(Optional.empty());

        AppException appException = assertThrows(
                AppException.class, () -> dynamicPricingService.getShowtimePrices(SHOWTIME_ID));

        assertEquals(HttpStatus.NOT_FOUND, appException.getStatus());
    }

    @Test
    void simulationPricesWithoutShowtimeAndUsesTheGivenScreenType() {
        PriceRule imaxRule = new PriceRule();
        imaxRule.setId(2L);
        imaxRule.setRuleName("Phụ thu IMAX");
        imaxRule.setScreenType(ScreenType.IMAX);
        imaxRule.setSurchargeAmount(new BigDecimal("30000"));
        when(priceRuleRepository.findByDeletedFalse()).thenReturn(List.of(imaxRule));
        givenSeatTypes();

        ShowtimePriceResponse showtimePriceResponse = dynamicPricingService.simulatePrices(
                new PriceSimulationRequest(new BigDecimal("100000"), ScreenType.IMAX, SATURDAY_EVENING));

        assertEquals(null, showtimePriceResponse.showtimeId());
        assertEquals(0, new BigDecimal("30000").compareTo(showtimePriceResponse.screenTypeSurcharge()));
        assertEquals(0, new BigDecimal("150000").compareTo(showtimePriceResponse.seatTypePrices().get(1).totalPrice()));
    }
}
