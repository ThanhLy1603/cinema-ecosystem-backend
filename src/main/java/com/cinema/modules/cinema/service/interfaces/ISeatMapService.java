package com.cinema.modules.cinema.service.interfaces;

import com.cinema.modules.cinema.dto.request.GenerateSeatsRequest;
import com.cinema.modules.cinema.dto.request.SeatMapRequest;
import com.cinema.modules.cinema.dto.response.SeatLayoutResponse;

public interface ISeatMapService {

    SeatLayoutResponse getSeatMap(Long actorUserId, Long hallId);

    SeatLayoutResponse saveSeatMap(Long actorUserId, Long hallId, SeatMapRequest seatMapRequest);

    SeatLayoutResponse generateSeats(Long actorUserId, Long hallId, GenerateSeatsRequest generateSeatsRequest);
}
