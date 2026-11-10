package com.cinema.modules.booking.service.interfaces;

import com.cinema.modules.booking.dto.response.ShowtimeSeatMapResponse;

public interface IShowtimeSeatMapService {

    ShowtimeSeatMapResponse getSeatMap(Long showtimeId);
}
