package com.cinema.modules.booking.service.interfaces;

import com.cinema.modules.booking.dto.response.SeatHoldResponse;

public interface ISeatHoldService {

    SeatHoldResponse holdSeat(Long userId, Long showtimeId, Long seatId);

    SeatHoldResponse releaseSeat(Long userId, Long showtimeId, Long seatId);

    SeatHoldResponse getMyHold(Long userId, Long showtimeId);

    void cancelPendingBooking(Long userId);
}
