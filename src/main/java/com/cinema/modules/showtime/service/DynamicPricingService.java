package com.cinema.modules.showtime.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.dto.response.SeatPricingInfoResponse;
import com.cinema.modules.cinema.dto.response.SeatTypeSurchargeResponse;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.showtime.dto.request.PriceSimulationRequest;
import com.cinema.modules.showtime.dto.response.SeatPriceResponse;
import com.cinema.modules.showtime.dto.response.SeatTypePriceResponse;
import com.cinema.modules.showtime.dto.response.ShowtimePriceResponse;
import com.cinema.modules.showtime.entity.PriceRule;
import com.cinema.modules.showtime.entity.Showtime;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import com.cinema.modules.showtime.model.ShowtimeSurchargeBreakdown;
import com.cinema.modules.showtime.repository.PriceRuleRepository;
import com.cinema.modules.showtime.repository.ShowtimeRepository;
import com.cinema.modules.showtime.service.interfaces.IDynamicPricingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class DynamicPricingService implements IDynamicPricingService {

    private final ShowtimeRepository showtimeRepository;
    private final PriceRuleRepository priceRuleRepository;
    private final DynamicPricingCalculator dynamicPricingCalculator;
    private final ICinemaQueryService cinemaQueryService;

    public DynamicPricingService(
            ShowtimeRepository showtimeRepository,
            PriceRuleRepository priceRuleRepository,
            DynamicPricingCalculator dynamicPricingCalculator,
            ICinemaQueryService cinemaQueryService) {
        this.showtimeRepository = showtimeRepository;
        this.priceRuleRepository = priceRuleRepository;
        this.dynamicPricingCalculator = dynamicPricingCalculator;
        this.cinemaQueryService = cinemaQueryService;
    }

    @Transactional(readOnly = true)
    @Override
    public ShowtimePriceResponse getShowtimePrices(Long showtimeId) {
        Showtime showtime = loadActiveShowtime(showtimeId);
        ScreenType screenType = loadHallInfo(showtime).screenType();
        return buildShowtimePrice(
                showtimeId, showtime.getBasePrice(), screenType, showtime.getStartTime());
    }

    @Transactional(readOnly = true)
    @Override
    public SeatPriceResponse calculateSeatPrice(Long showtimeId, Long seatId) {
        Showtime showtime = loadActiveShowtime(showtimeId);
        ScreenType screenType = loadHallInfo(showtime).screenType();
        SeatPricingInfoResponse seatPricingInfoResponse =
                cinemaQueryService.findSeatPricingInfos(List.of(seatId)).get(seatId);
        if (seatPricingInfoResponse == null) {
            throw new AppException("Không tìm thấy ghế", HttpStatus.NOT_FOUND, "SEAT_NOT_FOUND");
        }
        if (!seatPricingInfoResponse.hallId().equals(showtime.getHallId())) {
            throw new AppException(
                    "Ghế không thuộc phòng chiếu của suất chiếu này", HttpStatus.CONFLICT, "SEAT_NOT_IN_SHOWTIME_HALL");
        }
        if (!seatPricingInfoResponse.bookable()) {
            throw new AppException("Ghế đang bảo trì hoặc đã bị gỡ", HttpStatus.CONFLICT, "SEAT_NOT_BOOKABLE");
        }
        ShowtimeSurchargeBreakdown showtimeSurchargeBreakdown = calculateSurcharges(screenType, showtime.getStartTime());
        BigDecimal totalPrice = showtime.getBasePrice()
                .add(seatPricingInfoResponse.surcharge())
                .add(showtimeSurchargeBreakdown.total());
        return new SeatPriceResponse(
                showtimeId,
                seatId,
                seatPricingInfoResponse.seatTypeCode(),
                seatPricingInfoResponse.seatTypeName(),
                showtime.getBasePrice(),
                seatPricingInfoResponse.surcharge(),
                showtimeSurchargeBreakdown.timeSlotSurcharge(),
                showtimeSurchargeBreakdown.dayTypeSurcharge(),
                showtimeSurchargeBreakdown.screenTypeSurcharge(),
                totalPrice,
                showtimeSurchargeBreakdown.appliedRules());
    }

    @Transactional(readOnly = true)
    @Override
    public ShowtimePriceResponse simulatePrices(PriceSimulationRequest priceSimulationRequest) {
        return buildShowtimePrice(
                null,
                priceSimulationRequest.basePrice(),
                priceSimulationRequest.screenType(),
                priceSimulationRequest.startTime());
    }

    private ShowtimePriceResponse buildShowtimePrice(
            Long showtimeId, BigDecimal basePrice, ScreenType screenType, Instant startTime) {
        ShowtimeSurchargeBreakdown showtimeSurchargeBreakdown = calculateSurcharges(screenType, startTime);
        BigDecimal showtimeSurchargeTotal = showtimeSurchargeBreakdown.total();
        List<SeatTypePriceResponse> seatTypePriceResponses = cinemaQueryService.findSeatTypeSurcharges().stream()
                .map(seatTypeSurchargeResponse -> toSeatTypePrice(
                        seatTypeSurchargeResponse, basePrice.add(showtimeSurchargeTotal)))
                .toList();
        return new ShowtimePriceResponse(
                showtimeId,
                screenType,
                startTime,
                basePrice,
                showtimeSurchargeBreakdown.timeSlotSurcharge(),
                showtimeSurchargeBreakdown.dayTypeSurcharge(),
                showtimeSurchargeBreakdown.screenTypeSurcharge(),
                showtimeSurchargeBreakdown.appliedRules(),
                seatTypePriceResponses);
    }

    private SeatTypePriceResponse toSeatTypePrice(
            SeatTypeSurchargeResponse seatTypeSurchargeResponse, BigDecimal priceBeforeSeatSurcharge) {
        return new SeatTypePriceResponse(
                seatTypeSurchargeResponse.code(),
                seatTypeSurchargeResponse.name(),
                seatTypeSurchargeResponse.surcharge(),
                priceBeforeSeatSurcharge.add(seatTypeSurchargeResponse.surcharge()));
    }

    private ShowtimeSurchargeBreakdown calculateSurcharges(ScreenType screenType, Instant startTime) {
        List<PriceRule> priceRules = priceRuleRepository.findByDeletedFalse();
        return dynamicPricingCalculator.calculate(priceRules, screenType, startTime);
    }

    private Showtime loadActiveShowtime(Long showtimeId) {
        Showtime showtime = showtimeRepository.findById(showtimeId).orElseThrow(
                () -> new AppException("Không tìm thấy suất chiếu", HttpStatus.NOT_FOUND, "SHOWTIME_NOT_FOUND"));
        if (showtime.getStatus() != ShowtimeStatus.ACTIVE) {
            throw new AppException("Không tìm thấy suất chiếu", HttpStatus.NOT_FOUND, "SHOWTIME_NOT_FOUND");
        }
        return showtime;
    }

    private HallInfoResponse loadHallInfo(Showtime showtime) {
        HallInfoResponse hallInfoResponse =
                cinemaQueryService.findHallInfos(List.of(showtime.getHallId())).get(showtime.getHallId());
        if (hallInfoResponse == null || hallInfoResponse.deleted()) {
            throw new AppException("Không tìm thấy phòng chiếu", HttpStatus.NOT_FOUND, "HALL_NOT_FOUND");
        }
        return hallInfoResponse;
    }
}
