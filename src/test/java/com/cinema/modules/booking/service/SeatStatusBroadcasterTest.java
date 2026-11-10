package com.cinema.modules.booking.service;

import com.cinema.modules.booking.dto.message.SeatStateChange;
import com.cinema.modules.booking.dto.message.SeatStatusMessage;
import com.cinema.modules.booking.enums.SeatAvailability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SeatStatusBroadcasterTest {

    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    private SeatStatusBroadcaster seatStatusBroadcaster;

    @BeforeEach
    void setUp() {
        seatStatusBroadcaster = new SeatStatusBroadcaster(simpMessagingTemplate, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void broadcastSendsChangesToTheTopicOfThatShowtimeOnly() {
        seatStatusBroadcaster.broadcast(5L, List.of(new SeatStateChange(11L, SeatAvailability.HELD)));

        ArgumentCaptor<SeatStatusMessage> messageCaptor = ArgumentCaptor.forClass(SeatStatusMessage.class);
        verify(simpMessagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/showtimes/5/seats"),
                messageCaptor.capture());
        assertEquals(5L, messageCaptor.getValue().showtimeId());
        assertEquals(11L, messageCaptor.getValue().changes().get(0).seatId());
        assertEquals(SeatAvailability.HELD, messageCaptor.getValue().changes().get(0).state());
        assertEquals(NOW, messageCaptor.getValue().occurredAt());
    }

    @Test
    void emptyChangesSendNothing() {
        seatStatusBroadcaster.broadcast(5L, List.of());

        verify(simpMessagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
