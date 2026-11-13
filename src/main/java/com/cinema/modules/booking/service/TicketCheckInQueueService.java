package com.cinema.modules.booking.service;

import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.booking.dto.response.TicketQueueBookingResponse;
import com.cinema.modules.booking.dto.response.TicketQueueResponse;
import com.cinema.modules.booking.dto.response.TicketQueueShowtimeGroupResponse;
import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.entity.Ticket;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.repository.BookingRepository;
import com.cinema.modules.booking.repository.TicketRepository;
import com.cinema.modules.booking.service.interfaces.ITicketCheckInQueueService;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.dto.response.SeatLabelInfoResponse;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import com.cinema.modules.showtime.dto.response.ShowtimeInfoResponse;
import com.cinema.modules.showtime.service.interfaces.IShowtimeQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TicketCheckInQueueService implements ITicketCheckInQueueService {

    private static final Duration QUEUE_HORIZON = Duration.ofHours(24);

    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final IBranchAccessService branchAccessService;
    private final IShowtimeQueryService showtimeQueryService;
    private final IMovieQueryService movieQueryService;
    private final ICinemaQueryService cinemaQueryService;
    private final Clock clock;

    public TicketCheckInQueueService(
            BookingRepository bookingRepository,
            TicketRepository ticketRepository,
            IBranchAccessService branchAccessService,
            IShowtimeQueryService showtimeQueryService,
            IMovieQueryService movieQueryService,
            ICinemaQueryService cinemaQueryService,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.ticketRepository = ticketRepository;
        this.branchAccessService = branchAccessService;
        this.showtimeQueryService = showtimeQueryService;
        this.movieQueryService = movieQueryService;
        this.cinemaQueryService = cinemaQueryService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public TicketQueueResponse findQueue(Long staffUserId, Long cinemaId) {
        branchAccessService.assertCanAccessCinema(staffUserId, cinemaId);
        Instant now = Instant.now(clock);
        List<ShowtimeInfoResponse> showtimeInfoResponses =
                showtimeQueryService.findActiveShowtimeInfosBetween(now, now.plus(QUEUE_HORIZON));
        if (showtimeInfoResponses.isEmpty()) {
            return new TicketQueueResponse(now, List.of());
        }
        List<Booking> bookings = bookingRepository.findWithUncheckedTickets(
                cinemaId, BookingStatus.PAID, showtimeInfoResponses.stream().map(ShowtimeInfoResponse::id).toList());
        if (bookings.isEmpty()) {
            return new TicketQueueResponse(now, List.of());
        }
        Map<Long, List<Ticket>> ticketsByBookingId = ticketRepository
                .findByBookingIdInOrderBySeatIdAsc(bookings.stream().map(Booking::getId).toList()).stream()
                .collect(Collectors.groupingBy(Ticket::getBookingId));
        Map<Long, SeatLabelInfoResponse> seatLabelInfoResponses = cinemaQueryService.findSeatLabelInfos(
                ticketsByBookingId.values().stream().flatMap(List::stream).map(Ticket::getSeatId).distinct().toList());
        Map<Long, List<Booking>> bookingsByShowtimeId =
                bookings.stream().collect(Collectors.groupingBy(Booking::getShowtimeId));
        List<ShowtimeInfoResponse> activeShowtimeInfoResponses = showtimeInfoResponses.stream()
                .filter(showtimeInfoResponse -> bookingsByShowtimeId.containsKey(showtimeInfoResponse.id()))
                .toList();
        Map<Long, MovieInfoResponse> movieInfoResponses = movieQueryService.findMovieInfos(
                activeShowtimeInfoResponses.stream().map(ShowtimeInfoResponse::movieId).distinct().toList());
        Map<Long, HallInfoResponse> hallInfoResponses = cinemaQueryService.findHallInfos(
                activeShowtimeInfoResponses.stream().map(ShowtimeInfoResponse::hallId).distinct().toList());
        List<TicketQueueShowtimeGroupResponse> groups = new ArrayList<>();
        for (ShowtimeInfoResponse showtimeInfoResponse : activeShowtimeInfoResponses) {
            List<TicketQueueBookingResponse> queueBookings = bookingsByShowtimeId.get(showtimeInfoResponse.id())
                    .stream()
                    .map(booking -> toQueueBooking(
                            booking, ticketsByBookingId.getOrDefault(booking.getId(), List.of()),
                            seatLabelInfoResponses))
                    .toList();
            Instant checkInOpensAt = showtimeInfoResponse.startTime().minus(TicketCheckInService.EARLY_ENTRY_WINDOW);
            MovieInfoResponse movieInfoResponse = movieInfoResponses.get(showtimeInfoResponse.movieId());
            HallInfoResponse hallInfoResponse = hallInfoResponses.get(showtimeInfoResponse.hallId());
            groups.add(new TicketQueueShowtimeGroupResponse(
                    showtimeInfoResponse.id(),
                    movieInfoResponse == null ? "Phim #" + showtimeInfoResponse.movieId() : movieInfoResponse.title(),
                    hallInfoResponse == null ? "Phòng #" + showtimeInfoResponse.hallId() : hallInfoResponse.name(),
                    showtimeInfoResponse.startTime(),
                    showtimeInfoResponse.endTime(),
                    checkInOpensAt,
                    !now.isBefore(checkInOpensAt),
                    queueBookings.size(),
                    queueBookings.stream().mapToInt(TicketQueueBookingResponse::pendingTicketCount).sum(),
                    queueBookings));
        }
        return new TicketQueueResponse(now, groups);
    }

    private TicketQueueBookingResponse toQueueBooking(
            Booking booking, List<Ticket> tickets, Map<Long, SeatLabelInfoResponse> seatLabelInfoResponses) {
        List<String> pendingSeatLabels = tickets.stream()
                .filter(ticket -> !ticket.isCheckedIn())
                .map(ticket -> toSeatLabel(seatLabelInfoResponses.get(ticket.getSeatId()), ticket.getSeatId()))
                .toList();
        return new TicketQueueBookingResponse(
                booking.getBookingCode(), pendingSeatLabels.size(), tickets.size(), pendingSeatLabels);
    }

    private String toSeatLabel(SeatLabelInfoResponse seatLabelInfoResponse, Long seatId) {
        return seatLabelInfoResponse == null
                ? "#" + seatId
                : seatLabelInfoResponse.row() + seatLabelInfoResponse.number();
    }
}
