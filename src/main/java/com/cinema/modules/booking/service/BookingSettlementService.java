package com.cinema.modules.booking.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.booking.dto.message.SeatStateChange;
import com.cinema.modules.booking.dto.response.PayableBookingResponse;
import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.entity.SeatReservation;
import com.cinema.modules.booking.entity.Ticket;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.enums.SeatAvailability;
import com.cinema.modules.booking.enums.SeatReservationStatus;
import com.cinema.modules.booking.enums.SettlementOutcome;
import com.cinema.modules.booking.event.BookingPaidEvent;
import com.cinema.modules.booking.repository.BookingFnbDetailRepository;
import com.cinema.modules.booking.repository.BookingRepository;
import com.cinema.modules.booking.repository.SeatReservationRepository;
import com.cinema.modules.booking.repository.TicketRepository;
import com.cinema.modules.booking.service.interfaces.IBookingSettlementService;
import com.cinema.modules.booking.service.interfaces.ISeatStatusBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class BookingSettlementService implements IBookingSettlementService {

    private final BookingRepository bookingRepository;
    private final SeatReservationRepository seatReservationRepository;
    private final TicketRepository ticketRepository;
    private final BookingFnbDetailRepository bookingFnbDetailRepository;
    private final SeatGapChecker seatGapChecker;
    private final TicketCodeGenerator ticketCodeGenerator;
    private final ISeatStatusBroadcaster seatStatusBroadcaster;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public BookingSettlementService(
            BookingRepository bookingRepository,
            SeatReservationRepository seatReservationRepository,
            TicketRepository ticketRepository,
            BookingFnbDetailRepository bookingFnbDetailRepository,
            SeatGapChecker seatGapChecker,
            TicketCodeGenerator ticketCodeGenerator,
            ISeatStatusBroadcaster seatStatusBroadcaster,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.seatReservationRepository = seatReservationRepository;
        this.ticketRepository = ticketRepository;
        this.bookingFnbDetailRepository = bookingFnbDetailRepository;
        this.seatGapChecker = seatGapChecker;
        this.ticketCodeGenerator = ticketCodeGenerator;
        this.seatStatusBroadcaster = seatStatusBroadcaster;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    @Transactional
    @Override
    public PayableBookingResponse requirePayableBooking(Long userId) {
        Instant now = Instant.now(clock);
        Booking booking = bookingRepository.findByUserIdAndStatusForUpdate(userId, BookingStatus.PENDING)
                .filter(pendingBooking -> pendingBooking.getExpiresAt() != null
                        && pendingBooking.getExpiresAt().isAfter(now))
                .orElseThrow(this::noPendingBooking);
        boolean hasHeldSeats = seatReservationRepository.findByBookingIdOrderBySeatId(booking.getId()).stream()
                .anyMatch(seatReservation -> seatReservation.getStatus() == SeatReservationStatus.HELD);
        if (!hasHeldSeats) {
            throw noPendingBooking();
        }
        seatGapChecker.validateNoOrphanSeats(booking, now);
        if (booking.getFinalAmount() == null || booking.getFinalAmount().signum() <= 0) {
            throw new AppException(
                    "Giá trị đơn đặt vé không hợp lệ", HttpStatus.CONFLICT, "BOOKING_AMOUNT_INVALID");
        }
        return new PayableBookingResponse(
                booking.getId(), booking.getBookingCode(), booking.getFinalAmount(), booking.getExpiresAt());
    }

    @Transactional
    @Override
    public SettlementOutcome settlePaidBooking(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new AppException("Không tìm thấy đơn đặt vé", HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));
        if (booking.getStatus() == BookingStatus.PAID) {
            return SettlementOutcome.ALREADY_PAID;
        }
        if (booking.getStatus() == BookingStatus.REFUND_REQUIRED) {
            return SettlementOutcome.REFUND_REQUIRED;
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            return markRefundRequired(booking);
        }
        List<SeatReservation> seatReservations = seatReservationRepository.findByBookingIdOrderBySeatId(bookingId);
        boolean allSeatsStillHeld = !seatReservations.isEmpty()
                && seatReservations.stream()
                        .allMatch(seatReservation -> seatReservation.getStatus() == SeatReservationStatus.HELD);
        if (!allSeatsStillHeld) {
            return markRefundRequired(booking);
        }
        issueTickets(booking, seatReservations);
        return SettlementOutcome.PAID;
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<BookingStatus> findOwnedBookingStatus(Long bookingId, Long userId) {
        return bookingRepository.findById(bookingId)
                .filter(booking -> booking.getUserId().equals(userId))
                .map(Booking::getStatus);
    }

    private void issueTickets(Booking booking, List<SeatReservation> seatReservations) {
        seatReservationRepository.markBookedByBookingId(
                booking.getId(), SeatReservationStatus.HELD, SeatReservationStatus.BOOKED);
        BigDecimal fallbackSeatPrice = resolveFallbackSeatPrice(booking, seatReservations);
        List<Ticket> tickets = seatReservations.stream()
                .map(seatReservation -> toTicket(booking, seatReservation, fallbackSeatPrice))
                .toList();
        ticketRepository.saveAll(tickets);
        ticketRepository.flush();
        booking.setQrCode(ticketCodeGenerator.generateQrCode());
        booking.setStatus(BookingStatus.PAID);
        bookingRepository.save(booking);
        applicationEventPublisher.publishEvent(new BookingPaidEvent(
                booking.getId(), booking.getUserId(), booking.getBookingCode(), booking.getFinalAmount()));
        broadcastSoldAfterCommit(booking.getShowtimeId(), seatReservations);
    }

    private Ticket toTicket(Booking booking, SeatReservation seatReservation, BigDecimal fallbackSeatPrice) {
        Ticket ticket = new Ticket();
        ticket.setBookingId(booking.getId());
        ticket.setShowtimeId(seatReservation.getShowtimeId());
        ticket.setSeatId(seatReservation.getSeatId());
        ticket.setTicketCode(ticketCodeGenerator.generate());
        ticket.setPrice(seatReservation.getPrice() != null ? seatReservation.getPrice() : fallbackSeatPrice);
        return ticket;
    }

    private BigDecimal resolveFallbackSeatPrice(Booking booking, List<SeatReservation> seatReservations) {
        boolean hasMissingPrice = seatReservations.stream().anyMatch(seatReservation -> seatReservation.getPrice() == null);
        if (!hasMissingPrice) {
            return BigDecimal.ZERO;
        }
        BigDecimal fnbAmount = Objects.requireNonNullElse(
                bookingFnbDetailRepository.sumAmountByBookingId(booking.getId()), BigDecimal.ZERO);
        BigDecimal ticketAmount = Objects.requireNonNullElse(booking.getTotalAmount(), BigDecimal.ZERO).subtract(fnbAmount);
        return ticketAmount.divide(BigDecimal.valueOf(seatReservations.size()), 2, RoundingMode.HALF_UP);
    }

    private SettlementOutcome markRefundRequired(Booking booking) {
        log.warn("Đơn {} đã nhận thanh toán nhưng ghế không còn giữ được, cần hoàn tiền", booking.getBookingCode());
        booking.setStatus(BookingStatus.REFUND_REQUIRED);
        bookingRepository.save(booking);
        return SettlementOutcome.REFUND_REQUIRED;
    }

    private void broadcastSoldAfterCommit(Long showtimeId, List<SeatReservation> seatReservations) {
        List<SeatStateChange> seatStateChanges = seatReservations.stream()
                .map(seatReservation -> new SeatStateChange(seatReservation.getSeatId(), SeatAvailability.SOLD))
                .toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                seatStatusBroadcaster.broadcast(showtimeId, seatStateChanges);
            }
        });
    }

    private AppException noPendingBooking() {
        return new AppException(
                "Bạn chưa giữ ghế hoặc đã hết thời gian giữ ghế", HttpStatus.CONFLICT, "NO_PENDING_BOOKING");
    }
}
