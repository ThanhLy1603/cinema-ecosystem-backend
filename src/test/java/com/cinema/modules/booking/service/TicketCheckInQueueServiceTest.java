package com.cinema.modules.booking.service;

import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.booking.dto.response.TicketQueueResponse;
import com.cinema.modules.booking.dto.response.TicketQueueShowtimeGroupResponse;
import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.entity.Ticket;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.repository.BookingRepository;
import com.cinema.modules.booking.repository.TicketRepository;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.dto.response.SeatLabelInfoResponse;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import com.cinema.modules.showtime.dto.response.ShowtimeInfoResponse;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import com.cinema.modules.showtime.service.interfaces.IShowtimeQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketCheckInQueueServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T13:00:00Z");
    private static final Long STAFF_USER_ID = 7L;
    private static final Long CINEMA_ID = 1L;
    private static final Long BOOKING_ID = 100L;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private IBranchAccessService branchAccessService;

    @Mock
    private IShowtimeQueryService showtimeQueryService;

    @Mock
    private IMovieQueryService movieQueryService;

    @Mock
    private ICinemaQueryService cinemaQueryService;

    private TicketCheckInQueueService ticketCheckInQueueService;

    @BeforeEach
    void setUp() {
        ticketCheckInQueueService = new TicketCheckInQueueService(
                bookingRepository,
                ticketRepository,
                branchAccessService,
                showtimeQueryService,
                movieQueryService,
                cinemaQueryService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void queueGroupsBookingsWithUncheckedTicketsByShowtime() {
        Instant start = NOW.plusSeconds(30 * 60);
        when(showtimeQueryService.findActiveShowtimeInfosBetween(any(), any())).thenReturn(List.of(
                new ShowtimeInfoResponse(20L, 3L, 5L, start, start.plusSeconds(7200), BigDecimal.TEN,
                        ShowtimeStatus.ACTIVE)));
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setBookingCode("BK62072B12C606");
        booking.setShowtimeId(20L);
        when(bookingRepository.findWithUncheckedTickets(CINEMA_ID, BookingStatus.PAID, List.of(20L)))
                .thenReturn(List.of(booking));
        Ticket checkedTicket = buildTicket(11L, true);
        Ticket pendingTicket = buildTicket(12L, false);
        when(ticketRepository.findByBookingIdInOrderBySeatIdAsc(List.of(BOOKING_ID)))
                .thenReturn(List.of(checkedTicket, pendingTicket));
        when(cinemaQueryService.findSeatLabelInfos(anyCollection())).thenReturn(Map.of(
                11L, new SeatLabelInfoResponse(11L, "K", 1, "STANDARD", "Ghế thường"),
                12L, new SeatLabelInfoResponse(12L, "K", 2, "STANDARD", "Ghế thường")));
        when(movieQueryService.findMovieInfos(anyCollection())).thenReturn(Map.of(
                3L, new MovieInfoResponse(3L, "Đứa con của thời tiết", "dua-con-cua-thoi-tiet", 120, AgeRating.P,
                        null, MovieStatus.NOW_SHOWING, false)));
        when(cinemaQueryService.findHallInfos(anyCollection())).thenReturn(Map.of(
                5L, new HallInfoResponse(5L, CINEMA_ID, "Phòng 1", ScreenType.SCREEN_2D, HallStatus.ACTIVE, false)));

        TicketQueueResponse ticketQueueResponse = ticketCheckInQueueService.findQueue(STAFF_USER_ID, CINEMA_ID);

        assertEquals(1, ticketQueueResponse.showtimes().size());
        TicketQueueShowtimeGroupResponse group = ticketQueueResponse.showtimes().get(0);
        assertEquals("Phòng 1", group.hallName());
        assertTrue(group.checkInOpen());
        assertEquals(start.minusSeconds(3600), group.checkInOpensAt());
        assertEquals(1, group.pendingBookingCount());
        assertEquals(1, group.pendingTicketCount());
        assertEquals(List.of("K2"), group.bookings().get(0).pendingSeatLabels());
        assertEquals(2, group.bookings().get(0).totalTicketCount());
    }

    @Test
    void queueIsNotOpenYetMoreThanOneHourBeforeShowtime() {
        Instant start = NOW.plusSeconds(3 * 3600);
        when(showtimeQueryService.findActiveShowtimeInfosBetween(any(), any())).thenReturn(List.of(
                new ShowtimeInfoResponse(20L, 3L, 5L, start, start.plusSeconds(7200), BigDecimal.TEN,
                        ShowtimeStatus.ACTIVE)));
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setBookingCode("BK62072B12C606");
        booking.setShowtimeId(20L);
        when(bookingRepository.findWithUncheckedTickets(anyLong(), any(), anyCollection()))
                .thenReturn(List.of(booking));
        when(ticketRepository.findByBookingIdInOrderBySeatIdAsc(anyCollection()))
                .thenReturn(List.of(buildTicket(11L, false)));
        when(cinemaQueryService.findSeatLabelInfos(anyCollection())).thenReturn(Map.of());
        when(movieQueryService.findMovieInfos(anyCollection())).thenReturn(Map.of());
        when(cinemaQueryService.findHallInfos(anyCollection())).thenReturn(Map.of());

        TicketQueueShowtimeGroupResponse group =
                ticketCheckInQueueService.findQueue(STAFF_USER_ID, CINEMA_ID).showtimes().get(0);

        assertFalse(group.checkInOpen());
        assertEquals(List.of("#11"), group.bookings().get(0).pendingSeatLabels());
    }

    @Test
    void queueIsEmptyWhenNoShowtimeIsInTheHorizon() {
        when(showtimeQueryService.findActiveShowtimeInfosBetween(any(), any())).thenReturn(List.of());

        assertTrue(ticketCheckInQueueService.findQueue(STAFF_USER_ID, CINEMA_ID).showtimes().isEmpty());
        verify(bookingRepository, never()).findWithUncheckedTickets(anyLong(), any(), any());
    }

    private Ticket buildTicket(Long seatId, boolean checkedIn) {
        Ticket ticket = new Ticket();
        ticket.setBookingId(BOOKING_ID);
        ticket.setSeatId(seatId);
        ticket.setCheckedIn(checkedIn);
        return ticket;
    }
}
