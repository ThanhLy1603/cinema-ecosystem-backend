package com.cinema.modules.booking.service.interfaces;

import com.cinema.modules.booking.dto.message.SeatStateChange;

import java.util.List;

public interface ISeatStatusBroadcaster {

    void broadcast(Long showtimeId, List<SeatStateChange> seatStateChanges);
}
