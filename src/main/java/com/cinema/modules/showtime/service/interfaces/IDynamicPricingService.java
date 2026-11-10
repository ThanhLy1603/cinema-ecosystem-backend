package com.cinema.modules.showtime.service.interfaces;

import com.cinema.modules.showtime.dto.request.PriceSimulationRequest;
import com.cinema.modules.showtime.dto.response.SeatPriceResponse;
import com.cinema.modules.showtime.dto.response.ShowtimePriceResponse;

public interface IDynamicPricingService {

    ShowtimePriceResponse getShowtimePrices(Long showtimeId);

    SeatPriceResponse calculateSeatPrice(Long showtimeId, Long seatId);

    ShowtimePriceResponse simulatePrices(PriceSimulationRequest priceSimulationRequest);
}
