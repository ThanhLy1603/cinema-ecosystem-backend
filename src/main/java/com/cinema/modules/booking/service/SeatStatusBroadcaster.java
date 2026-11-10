package com.cinema.modules.booking.service;

import com.cinema.modules.booking.dto.message.SeatStateChange;
import com.cinema.modules.booking.dto.message.SeatStatusMessage;
import com.cinema.modules.booking.service.interfaces.ISeatStatusBroadcaster;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class SeatStatusBroadcaster implements ISeatStatusBroadcaster {

    private static final String SEAT_TOPIC_TEMPLATE = "/topic/showtimes/%d/seats";

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final Clock clock;

    public SeatStatusBroadcaster(SimpMessagingTemplate simpMessagingTemplate, Clock clock) {
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.clock = clock;
    }

    @Override
    public void broadcast(Long showtimeId, List<SeatStateChange> seatStateChanges) {
        if (seatStateChanges == null || seatStateChanges.isEmpty()) {
            return;
        }
        simpMessagingTemplate.convertAndSend(
                SEAT_TOPIC_TEMPLATE.formatted(showtimeId),
                new SeatStatusMessage(showtimeId, List.copyOf(seatStateChanges), Instant.now(clock)));
    }
}
