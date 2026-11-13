package com.cinema.modules.booking.service;

import com.cinema.modules.booking.dto.response.BookingDetailResponse;
import com.cinema.modules.booking.dto.response.BookingFnbLineResponse;
import com.cinema.modules.booking.dto.response.BookingSeatResponse;
import com.cinema.modules.booking.dto.response.BookingShowtimeResponse;
import com.cinema.modules.booking.dto.response.BookingTicketResponse;
import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.entity.SeatReservation;
import com.cinema.modules.booking.entity.Ticket;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.repository.BookingRepository;
import com.cinema.modules.booking.repository.SeatReservationRepository;
import com.cinema.modules.booking.repository.TicketRepository;
import com.cinema.modules.booking.service.interfaces.IBookingDetailService;
import com.cinema.modules.booking.service.interfaces.IBookingFnbService;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.dto.response.SeatLabelInfoResponse;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import com.cinema.modules.showtime.dto.response.ShowtimeInfoResponse;
import com.cinema.modules.showtime.service.interfaces.IShowtimeQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class BookingDetailService implements IBookingDetailService {

    private static final List<BookingStatus> MY_BOOKING_STATUSES =
            List.of(BookingStatus.PAID, BookingStatus.REFUND_REQUIRED, BookingStatus.REFUNDED);
    private static final int MAX_MY_BOOKINGS = 50;

    private final BookingRepository bookingRepository;
    private final SeatReservationRepository seatReservationRepository;
    private final TicketRepository ticketRepository;
    private final IBookingFnbService bookingFnbService;
    private final IShowtimeQueryService showtimeQueryService;
    private final IMovieQueryService movieQueryService;
    private final ICinemaQueryService cinemaQueryService;
    private final QrCodeGenerator qrCodeGenerator;
    private final Clock clock;

    public BookingDetailService(
            BookingRepository bookingRepository,
            SeatReservationRepository seatReservationRepository,
            TicketRepository ticketRepository,
            IBookingFnbService bookingFnbService,
            IShowtimeQueryService showtimeQueryService,
            IMovieQueryService movieQueryService,
            ICinemaQueryService cinemaQueryService,
            QrCodeGenerator qrCodeGenerator,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.seatReservationRepository = seatReservationRepository;
        this.ticketRepository = ticketRepository;
        this.bookingFnbService = bookingFnbService;
        this.showtimeQueryService = showtimeQueryService;
        this.movieQueryService = movieQueryService;
        this.cinemaQueryService = cinemaQueryService;
        this.qrCodeGenerator = qrCodeGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<BookingDetailResponse> findPendingBooking(Long userId) {
        Instant now = Instant.now(clock);
        return bookingRepository.findFirstByUserIdAndStatus(userId, BookingStatus.PENDING)
                .filter(booking -> booking.getExpiresAt() != null && booking.getExpiresAt().isAfter(now))
                .filter(booking -> booking.getShowtimeId() != null)
                .flatMap(booking -> toBookingDetail(booking, now));
    }

    @Transactional(readOnly = true)
    @Override
    public List<BookingDetailResponse> findMyBookings(Long userId) {
        Instant now = Instant.now(clock);
        return bookingRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(userId, MY_BOOKING_STATUSES).stream()
                .limit(MAX_MY_BOOKINGS)
                .map(booking -> toBookingDetail(booking, now))
                .flatMap(Optional::stream)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<BookingDetailResponse> findBookingDetailByCode(String bookingCode) {
        return bookingRepository.findByBookingCode(bookingCode)
                .flatMap(booking -> toBookingDetail(booking, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<BookingDetailResponse> findBookingDetail(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .flatMap(booking -> toBookingDetail(booking, Instant.now(clock)));
    }

    private Optional<BookingDetailResponse> toBookingDetail(Booking booking, Instant now) {
        Optional<BookingShowtimeResponse> bookingShowtimeResponse = toBookingShowtime(booking.getShowtimeId());
        if (bookingShowtimeResponse.isEmpty()) {
            return Optional.empty();
        }
        List<Ticket> tickets = ticketRepository.findByBookingIdOrderBySeatIdAsc(booking.getId());
        List<SeatReservation> seatReservations = seatReservationRepository.findByBookingIdOrderBySeatId(booking.getId());
        Map<Long, SeatLabelInfoResponse> seatLabelInfoResponses = cinemaQueryService.findSeatLabelInfos(
                Stream.concat(
                                tickets.stream().map(Ticket::getSeatId),
                                seatReservations.stream().map(SeatReservation::getSeatId))
                        .distinct()
                        .toList());
        List<BookingSeatResponse> bookingSeatResponses = (tickets.isEmpty()
                        ? seatReservations.stream()
                                .map(seatReservation -> toBookingSeat(
                                        seatReservation.getSeatId(),
                                        seatReservation.getPrice(),
                                        seatLabelInfoResponses))
                        : tickets.stream()
                                .map(ticket -> toBookingSeat(ticket.getSeatId(), ticket.getPrice(), seatLabelInfoResponses)))
                .sorted(Comparator.comparing(BookingSeatResponse::label, BookingDetailService::compareSeatLabels))
                .toList();
        List<BookingFnbLineResponse> bookingFnbLineResponses = bookingFnbService.findFnbLines(booking.getId());
        BigDecimal ticketAmount = bookingSeatResponses.stream()
                .map(BookingSeatResponse::price)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fnbAmount = bookingFnbLineResponses.stream()
                .map(BookingFnbLineResponse::lineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discountAmount = Objects.requireNonNullElse(booking.getDiscountAmount(), BigDecimal.ZERO);
        boolean pending = booking.getStatus() == BookingStatus.PENDING;
        BigDecimal totalAmount = pending ? ticketAmount.add(fnbAmount) : booking.getTotalAmount();
        BigDecimal finalAmount = pending ? totalAmount.subtract(discountAmount) : booking.getFinalAmount();
        List<BookingTicketResponse> bookingTicketResponses = tickets.stream()
                .map(ticket -> toBookingTicket(ticket, seatLabelInfoResponses))
                .toList();
        return Optional.of(new BookingDetailResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getCreatedAt(),
                booking.getExpiresAt(),
                now,
                bookingShowtimeResponse.get(),
                bookingSeatResponses,
                bookingFnbLineResponses,
                ticketAmount,
                fnbAmount,
                discountAmount,
                totalAmount,
                finalAmount,
                bookingTicketResponses,
                booking.getStatus() == BookingStatus.PAID && booking.getQrCode() != null
                        ? qrCodeGenerator.toPngDataUrl(booking.getQrCode(), QrCodeGenerator.DEFAULT_SIZE)
                        : null));
    }

    private Optional<BookingShowtimeResponse> toBookingShowtime(Long showtimeId) {
        if (showtimeId == null) {
            return Optional.empty();
        }
        Optional<ShowtimeInfoResponse> showtimeInfoResponse = showtimeQueryService.findShowtimeInfo(showtimeId);
        if (showtimeInfoResponse.isEmpty()) {
            return Optional.empty();
        }
        ShowtimeInfoResponse showtime = showtimeInfoResponse.get();
        MovieInfoResponse movieInfoResponse =
                movieQueryService.findMovieInfos(List.of(showtime.movieId())).get(showtime.movieId());
        HallInfoResponse hallInfoResponse =
                cinemaQueryService.findHallInfos(List.of(showtime.hallId())).get(showtime.hallId());
        if (movieInfoResponse == null || hallInfoResponse == null) {
            return Optional.empty();
        }
        String cinemaName = cinemaQueryService.findCinemaNames(List.of(hallInfoResponse.cinemaId()))
                .get(hallInfoResponse.cinemaId());
        return Optional.of(new BookingShowtimeResponse(
                showtime.id(),
                showtime.startTime(),
                showtime.endTime(),
                movieInfoResponse.id(),
                movieInfoResponse.slug(),
                movieInfoResponse.title(),
                movieInfoResponse.posterUrl(),
                movieInfoResponse.rated(),
                movieInfoResponse.durationMinutes(),
                hallInfoResponse.cinemaId(),
                cinemaName,
                hallInfoResponse.id(),
                hallInfoResponse.name(),
                hallInfoResponse.screenType()));
    }

    private BookingSeatResponse toBookingSeat(
            Long seatId, BigDecimal price, Map<Long, SeatLabelInfoResponse> seatLabelInfoResponses) {
        SeatLabelInfoResponse seatLabelInfoResponse = seatLabelInfoResponses.get(seatId);
        return new BookingSeatResponse(
                seatId,
                toSeatLabel(seatLabelInfoResponse, seatId),
                seatLabelInfoResponse == null ? null : seatLabelInfoResponse.seatTypeName(),
                seatLabelInfoResponse == null ? null : seatLabelInfoResponse.seatTypeCode(),
                price);
    }

    private BookingTicketResponse toBookingTicket(
            Ticket ticket, Map<Long, SeatLabelInfoResponse> seatLabelInfoResponses) {
        return new BookingTicketResponse(
                ticket.getId(),
                ticket.getTicketCode(),
                ticket.getSeatId(),
                toSeatLabel(seatLabelInfoResponses.get(ticket.getSeatId()), ticket.getSeatId()),
                ticket.getPrice(),
                ticket.isCheckedIn(),
                ticket.getCheckedInAt());
    }

    private String toSeatLabel(SeatLabelInfoResponse seatLabelInfoResponse, Long seatId) {
        return seatLabelInfoResponse == null
                ? "#" + seatId
                : seatLabelInfoResponse.row() + seatLabelInfoResponse.number();
    }

    private static int compareSeatLabels(String firstSeatLabel, String secondSeatLabel) {
        String firstRow = firstSeatLabel.replaceAll("\\d+$", "");
        String secondRow = secondSeatLabel.replaceAll("\\d+$", "");
        int rowComparison = firstRow.compareTo(secondRow);
        if (rowComparison != 0) {
            return rowComparison;
        }
        return Integer.compare(
                parseSeatNumber(firstSeatLabel, firstRow), parseSeatNumber(secondSeatLabel, secondRow));
    }

    private static int parseSeatNumber(String seatLabel, String row) {
        String numberText = seatLabel.substring(row.length());
        return numberText.isEmpty() ? 0 : Integer.parseInt(numberText);
    }
}
