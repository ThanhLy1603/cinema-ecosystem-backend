package com.cinema.modules.booking.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.audit.enums.AuditAction;
import com.cinema.modules.audit.service.interfaces.IAuditLogService;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.booking.dto.message.SeatStateChange;
import com.cinema.modules.booking.dto.response.BookingDetailResponse;
import com.cinema.modules.booking.dto.response.BookingExceptionResponse;
import com.cinema.modules.booking.dto.response.BookingFnbLineResponse;
import com.cinema.modules.booking.dto.response.BookingTicketResponse;
import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.entity.BookingRefund;
import com.cinema.modules.booking.entity.Ticket;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.enums.SeatAvailability;
import com.cinema.modules.booking.event.BookingRefundedEvent;
import com.cinema.modules.booking.event.TicketReissuedEvent;
import com.cinema.modules.booking.repository.BookingRefundRepository;
import com.cinema.modules.booking.repository.BookingRepository;
import com.cinema.modules.booking.repository.SeatReservationRepository;
import com.cinema.modules.booking.repository.TicketRepository;
import com.cinema.modules.booking.service.interfaces.IBookingDetailService;
import com.cinema.modules.booking.service.interfaces.IBookingExceptionService;
import com.cinema.modules.booking.service.interfaces.ISeatStatusBroadcaster;
import com.cinema.modules.payment.dto.response.RefundedPaymentResponse;
import com.cinema.modules.payment.enums.PaymentMethod;
import com.cinema.modules.payment.service.interfaces.IPaymentQueryService;
import com.cinema.modules.payment.service.interfaces.IPaymentRefundService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class BookingExceptionService implements IBookingExceptionService {

    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final SeatReservationRepository seatReservationRepository;
    private final BookingRefundRepository bookingRefundRepository;
    private final IBookingDetailService bookingDetailService;
    private final IBranchAccessService branchAccessService;
    private final IPaymentQueryService paymentQueryService;
    private final IPaymentRefundService paymentRefundService;
    private final ISeatStatusBroadcaster seatStatusBroadcaster;
    private final IAuditLogService auditLogService;
    private final TicketCodeGenerator ticketCodeGenerator;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public BookingExceptionService(
            BookingRepository bookingRepository,
            TicketRepository ticketRepository,
            SeatReservationRepository seatReservationRepository,
            BookingRefundRepository bookingRefundRepository,
            IBookingDetailService bookingDetailService,
            IBranchAccessService branchAccessService,
            IPaymentQueryService paymentQueryService,
            IPaymentRefundService paymentRefundService,
            ISeatStatusBroadcaster seatStatusBroadcaster,
            IAuditLogService auditLogService,
            TicketCodeGenerator ticketCodeGenerator,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.ticketRepository = ticketRepository;
        this.seatReservationRepository = seatReservationRepository;
        this.bookingRefundRepository = bookingRefundRepository;
        this.bookingDetailService = bookingDetailService;
        this.branchAccessService = branchAccessService;
        this.paymentQueryService = paymentQueryService;
        this.paymentRefundService = paymentRefundService;
        this.seatStatusBroadcaster = seatStatusBroadcaster;
        this.auditLogService = auditLogService;
        this.ticketCodeGenerator = ticketCodeGenerator;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public BookingExceptionResponse lookup(Long managerUserId, String code) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        Booking booking = bookingRepository.findByBookingCode(normalizedCode)
                .or(() -> bookingRepository.findByQrCode(normalizedCode))
                .orElseThrow(this::bookingNotFound);
        branchAccessService.assertCanAccessCinema(managerUserId, booking.getCinemaId());
        return toResponse(booking);
    }

    @Transactional
    @Override
    public BookingExceptionResponse reissueTicket(Long managerUserId, String bookingCode, String reason) {
        Booking booking = lockBooking(managerUserId, bookingCode);
        BookingExceptionResponse currentResponse = toResponse(booking);
        if (!currentResponse.canReissue()) {
            throw new AppException(currentResponse.reissueBlockedReason(), HttpStatus.CONFLICT, "NOT_REISSUABLE");
        }
        booking.setQrCode(ticketCodeGenerator.generateQrCode());
        bookingRepository.save(booking);
        auditLogService.record(managerUserId, booking.getCinemaId(), AuditAction.REISSUE_TICKET, "BOOKING",
                booking.getBookingCode(), reason);
        applicationEventPublisher.publishEvent(
                new TicketReissuedEvent(booking.getId(), booking.getUserId(), booking.getBookingCode()));
        return toResponse(booking);
    }

    @Transactional
    @Override
    public BookingExceptionResponse refundBooking(Long managerUserId, String bookingCode, String reason) {
        Booking booking = lockBooking(managerUserId, bookingCode);
        BookingExceptionResponse currentResponse = toResponse(booking);
        if (!currentResponse.canRefund()) {
            throw new AppException(currentResponse.refundBlockedReason(), HttpStatus.CONFLICT, "NOT_REFUNDABLE");
        }
        Instant now = Instant.now(clock);
        List<Ticket> tickets = ticketRepository.findByBookingIdOrderBySeatIdAsc(booking.getId());
        List<SeatStateChange> seatStateChanges = tickets.stream()
                .map(ticket -> new SeatStateChange(ticket.getSeatId(), SeatAvailability.AVAILABLE))
                .toList();
        String seatLabels = String.join(", ", currentResponse.booking().tickets().stream()
                .map(BookingTicketResponse::seatLabel).toList());
        ticketRepository.deleteByBookingId(booking.getId());
        seatReservationRepository.deleteByBookingId(booking.getId());
        Optional<RefundedPaymentResponse> refundedPayment =
                paymentRefundService.markBookingPaymentRefunded(booking.getId());
        booking.setStatus(BookingStatus.REFUNDED);
        bookingRepository.save(booking);
        BookingRefund bookingRefund = new BookingRefund();
        bookingRefund.setBookingId(booking.getId());
        bookingRefund.setAmount(booking.getFinalAmount());
        bookingRefund.setPaymentMethod(refundedPayment.map(RefundedPaymentResponse::paymentMethod)
                .map(PaymentMethod::name).orElse(null));
        bookingRefund.setReason(reason);
        bookingRefund.setRefundedByUserId(managerUserId);
        bookingRefund.setCreatedAt(now);
        bookingRefundRepository.save(bookingRefund);
        auditLogService.record(managerUserId, booking.getCinemaId(), AuditAction.REFUND_BOOKING, "BOOKING",
                booking.getBookingCode(),
                booking.getFinalAmount().toPlainString() + " | ghế " + seatLabels + " | " + reason);
        applicationEventPublisher.publishEvent(new BookingRefundedEvent(
                booking.getId(), booking.getUserId(), booking.getBookingCode(), booking.getFinalAmount()));
        broadcastReleasedAfterCommit(booking.getShowtimeId(), seatStateChanges);
        return toResponse(booking);
    }

    private Booking lockBooking(Long managerUserId, String bookingCode) {
        Booking booking = bookingRepository.findByBookingCode(bookingCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(this::bookingNotFound);
        branchAccessService.assertCanAccessCinema(managerUserId, booking.getCinemaId());
        return bookingRepository.findByIdForUpdate(booking.getId()).orElseThrow(this::bookingNotFound);
    }

    private BookingExceptionResponse toResponse(Booking booking) {
        BookingDetailResponse bookingDetailResponse = bookingDetailService.findBookingDetail(booking.getId())
                .orElseThrow(this::bookingNotFound);
        Instant now = Instant.now(clock);
        int checkedInTicketCount = (int) bookingDetailResponse.tickets().stream()
                .filter(BookingTicketResponse::checkedIn)
                .count();
        boolean hasDeliveredFnb = bookingDetailResponse.fnbLines().stream()
                .anyMatch(bookingFnbLineResponse -> bookingFnbLineResponse.deliveredQuantity() > 0);
        String refundBlockedReason = findRefundBlockedReason(
                booking, bookingDetailResponse, checkedInTicketCount, hasDeliveredFnb, now);
        String reissueBlockedReason = findReissueBlockedReason(booking, bookingDetailResponse, now);
        Optional<BookingRefund> bookingRefund = bookingRefundRepository.findByBookingId(booking.getId());
        return new BookingExceptionResponse(
                bookingDetailResponse,
                booking.getSalesChannel(),
                paymentQueryService.findSuccessfulPaymentMethod(booking.getId())
                        .orElseGet(() -> bookingRefund.map(BookingRefund::getPaymentMethod)
                                .map(PaymentMethod::valueOf).orElse(null)),
                checkedInTicketCount,
                refundBlockedReason == null,
                refundBlockedReason,
                reissueBlockedReason == null,
                reissueBlockedReason,
                bookingRefund.map(BookingRefund::getAmount).orElse(null),
                bookingRefund.map(BookingRefund::getReason).orElse(null),
                bookingRefund.map(BookingRefund::getCreatedAt).orElse(null));
    }

    private String findRefundBlockedReason(
            Booking booking,
            BookingDetailResponse bookingDetailResponse,
            int checkedInTicketCount,
            boolean hasDeliveredFnb,
            Instant now) {
        if (booking.getStatus() == BookingStatus.REFUNDED) {
            return "Đơn này đã được hoàn tiền.";
        }
        if (booking.getStatus() != BookingStatus.PAID) {
            return "Chỉ hoàn tiền được đơn đã thanh toán.";
        }
        if (checkedInTicketCount > 0) {
            return "Đã có ghế vào rạp, hãy hoàn tác soát vé trước khi hoàn tiền.";
        }
        if (hasDeliveredFnb) {
            return "Đã giao bắp nước cho khách nên không hoàn tiền được.";
        }
        if (!now.isBefore(bookingDetailResponse.showtime().startTime())) {
            return "Suất chiếu đã bắt đầu nên không hoàn tiền được.";
        }
        return null;
    }

    private String findReissueBlockedReason(
            Booking booking, BookingDetailResponse bookingDetailResponse, Instant now) {
        if (booking.getStatus() != BookingStatus.PAID) {
            return "Chỉ cấp lại vé cho đơn đã thanh toán và chưa hoàn tiền.";
        }
        if (now.isAfter(bookingDetailResponse.showtime().endTime())) {
            return "Suất chiếu đã kết thúc.";
        }
        return null;
    }

    private AppException bookingNotFound() {
        return new AppException("Không tìm thấy đơn đặt vé", HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND");
    }

    private void broadcastReleasedAfterCommit(Long showtimeId, List<SeatStateChange> seatStateChanges) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                seatStatusBroadcaster.broadcast(showtimeId, seatStateChanges);
            }
        });
    }
}
