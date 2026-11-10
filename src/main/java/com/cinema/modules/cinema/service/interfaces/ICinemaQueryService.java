package com.cinema.modules.cinema.service.interfaces;

import com.cinema.modules.cinema.dto.response.CinemaOptionResponse;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.dto.response.SeatPricingInfoResponse;
import com.cinema.modules.cinema.dto.response.SeatTypeSurchargeResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ICinemaQueryService {

    Set<Long> findActiveCinemaIds(Collection<Long> cinemaIds);

    Set<Long> findAllCinemaIds();

    Map<Long, String> findCinemaNames(Collection<Long> cinemaIds);

    List<CinemaOptionResponse> findActiveCinemaOptions();

    Map<Long, HallInfoResponse> findHallInfos(Collection<Long> hallIds);

    Set<Long> findHallIdsByCinemaIds(Collection<Long> cinemaIds);

    Map<Long, Integer> countBookableSeatsByHall(Collection<Long> hallIds);

    Map<Long, SeatPricingInfoResponse> findSeatPricingInfos(Collection<Long> seatIds);

    List<SeatTypeSurchargeResponse> findSeatTypeSurcharges();
}
