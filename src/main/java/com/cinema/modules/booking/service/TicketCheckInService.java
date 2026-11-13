package com.cinema.modules.booking.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.audit.enums.AuditAction;
import com.cinema.modules.audit.service.interfaces.IAuditLogService;
import com.cinema.modules.auth.dto.response.UserContactResponse;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.auth.service.interfaces.IUserQueryService;
import com.cinema.modules.booking.dto.request.TicketCheckInRequest;
import com.cinema.modules.booking.dto.request.TicketScanRequest;
import com.cinema.modules.booking.dto.request.TicketUndoRequest;
import com.cinema.modules.booking.dto.response.BookingDetailResponse;
import com.cinema.modules.booking.dto.response.BookingSeatResponse;
import com.cinema.modules.booking.dto.response.BookingShowtimeResponse;
import com.cinema.modules.booking.dto.response.BookingTicketResponse;
import com.cinema.modules.booking.dto.response.BookingFnbLineResponse;
import com.cinema.modules.booking.dto.response.ScanBookingResponse;
import com.cinema.modules.booking.dto.response.ScanFnbLineResponse;
import com.cinema.modules.booking.dto.response.ScanTicketResponse;
import com.cinema.modules.booking.dto.response.TicketScanLogResponse;
import com.cinema.modules.booking.dto.response.TicketScanResponse;
import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.entity.Ticket;
import com.cinema.modules.booking.entity.TicketScanLog;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.enums.CheckInResultCode;
import com.cinema.modules.booking.enums.TicketScanAction;
import com.cinema.modules.booking.repository.BookingRepository;
import com.cinema.modules.booking.repository.TicketRepository;
import com.cinema.modules.booking.repository.TicketScanLogRepository;
import com.cinema.modules.booking.service.interfaces.IBookingDetailService;
import com.cinema.modules.booking.service.interfaces.ITicketCheckInService;
import com.cinema.modules.fnb.service.interfaces.IFnbQueryService;
import com.cinema.modules.payment.service.interfaces.IPaymentQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TicketCheckInService implements ITicketCheckInService {

    private static final String SCAN_CHANNEL = "CHECK_IN";
    static final Duration EARLY_ENTRY_WINDOW = Duration.ofMinutes(60);
    private static final int HISTORY_LIMIT = 50;
    private static final List<CheckInResultCode> HISTORY_RESULT_CODES =
            List.of(CheckInResultCode.CHECKED_IN, CheckInResultCode.UNDONE);
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final TicketScanLogRepository ticketScanLogRepository;
    private final IBookingDetailService bookingDetailService;
    private final IBranchAccessService branchAccessService;
    private final IUserQueryService userQueryService;
    private final IFnbQueryService fnbQueryService;
    private final IPaymentQueryService paymentQueryService;
    private final ScanGuard scanGuard;
    private final IAuditLogService auditLogService;
    private final Clock clock;

    public TicketCheckInService(
            BookingRepository bookingRepository,
            TicketRepository ticketRepository,
            TicketScanLogRepository ticketScanLogRepository,
            IBookingDetailService bookingDetailService,
            IBranchAccessService branchAccessService,
            IUserQueryService userQueryService,
            IFnbQueryService fnbQueryService,
            IPaymentQueryService paymentQueryService,
            ScanGuard scanGuard,
            IAuditLogService auditLogService,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.ticketRepository = ticketRepository;
        this.ticketScanLogRepository = ticketScanLogRepository;
        this.bookingDetailService = bookingDetailService;
        this.branchAccessService = branchAccessService;
        this.userQueryService = userQueryService;
        this.fnbQueryService = fnbQueryService;
        this.paymentQueryService = paymentQueryService;
        this.scanGuard = scanGuard;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    @Override
    public TicketScanResponse scan(Long staffUserId, TicketScanRequest ticketScanRequest) {
        assertStaffCanWorkAt(staffUserId, ticketScanRequest.cinemaId());
        scanGuard.assertScanAllowed(staffUserId, ticketScanRequest.cinemaId(), SCAN_CHANNEL);
        String code = normalizeCode(ticketScanRequest.code());
        Optional<Booking> optionalBooking = findBooking(code);
        if (optionalBooking.isEmpty()) {
            scanGuard.registerUnknownCode(staffUserId, ticketScanRequest.cinemaId(), SCAN_CHANNEL, code);
        }
        TicketScanResponse ticketScanResponse = optionalBooking
                .map(booking -> evaluate(staffUserId, ticketScanRequest.cinemaId(), booking))
                .orElseGet(this::notFound);
        writeLog(staffUserId, ticketScanRequest.cinemaId(), optionalBooking.orElse(null), code,
                TicketScanAction.LOOKUP, ticketScanResponse.resultCode(), 0);
        return ticketScanResponse;
    }

    @Transactional
    @Override
    public TicketScanResponse checkIn(Long staffUserId, TicketCheckInRequest ticketCheckInRequest) {
        assertStaffCanWorkAt(staffUserId, ticketCheckInRequest.cinemaId());
        scanGuard.assertScanAllowed(staffUserId, ticketCheckInRequest.cinemaId(), SCAN_CHANNEL);
        String code = normalizeCode(ticketCheckInRequest.code());
        Optional<Booking> optionalBooking = findBooking(code);
        if (optionalBooking.isEmpty()) {
            TicketScanResponse notFoundResponse = notFound();
            writeLog(staffUserId, ticketCheckInRequest.cinemaId(), null, code, TicketScanAction.CHECK_IN,
                    notFoundResponse.resultCode(), 0);
            return notFoundResponse;
        }
        Booking lockedBooking = bookingRepository.findByIdForUpdate(optionalBooking.get().getId())
                .orElseThrow(() -> new AppException("Không tìm thấy đơn đặt vé", HttpStatus.NOT_FOUND));
        TicketScanResponse currentResponse = evaluate(staffUserId, ticketCheckInRequest.cinemaId(), lockedBooking);
        if (!currentResponse.canCheckIn()) {
            writeLog(staffUserId, ticketCheckInRequest.cinemaId(), lockedBooking, code, TicketScanAction.CHECK_IN,
                    currentResponse.resultCode(), 0);
            return currentResponse;
        }
        List<Ticket> tickets = ticketRepository.findByBookingIdOrderBySeatIdAsc(lockedBooking.getId());
        List<Ticket> selectedTickets = selectTicketsToCheckIn(tickets, ticketCheckInRequest.ticketIds());
        if (selectedTickets.isEmpty()) {
            TicketScanResponse alreadyUsedResponse = new TicketScanResponse(
                    CheckInResultCode.ALREADY_USED,
                    "Các ghế đã chọn đã được xác nhận vào rạp trước đó.",
                    currentResponse.canCheckIn(),
                    currentResponse.pendingTicketCount(),
                    currentResponse.booking());
            writeLog(staffUserId, ticketCheckInRequest.cinemaId(), lockedBooking, code, TicketScanAction.CHECK_IN,
                    CheckInResultCode.ALREADY_USED, 0);
            return alreadyUsedResponse;
        }
        Instant now = Instant.now(clock);
        for (Ticket ticket : selectedTickets) {
            ticket.setCheckedIn(true);
            ticket.setCheckedInAt(now);
        }
        ticketRepository.saveAll(selectedTickets);
        writeLog(staffUserId, ticketCheckInRequest.cinemaId(), lockedBooking, code, TicketScanAction.CHECK_IN,
                CheckInResultCode.CHECKED_IN, selectedTickets.size());
        TicketScanResponse refreshedResponse = evaluate(staffUserId, ticketCheckInRequest.cinemaId(), lockedBooking);
        return new TicketScanResponse(
                CheckInResultCode.CHECKED_IN,
                "Đã xác nhận " + selectedTickets.size() + " ghế vào rạp.",
                refreshedResponse.canCheckIn(),
                refreshedResponse.pendingTicketCount(),
                refreshedResponse.booking());
    }

    @Transactional
    @Override
    public TicketScanResponse undoCheckIn(Long staffUserId, TicketUndoRequest ticketUndoRequest) {
        assertStaffCanWorkAt(staffUserId, ticketUndoRequest.cinemaId());
        String code = normalizeCode(ticketUndoRequest.code());
        Booking booking = findBooking(code)
                .orElseThrow(() -> new AppException("Không tìm thấy đơn đặt vé", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        Booking lockedBooking = bookingRepository.findByIdForUpdate(booking.getId())
                .orElseThrow(() -> new AppException("Không tìm thấy đơn đặt vé", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!lockedBooking.getCinemaId().equals(ticketUndoRequest.cinemaId())) {
            throw new AppException("Vé không thuộc chi nhánh đang làm việc", HttpStatus.FORBIDDEN, "WRONG_BRANCH");
        }
        Set<Long> requestedTicketIds = Set.copyOf(ticketUndoRequest.ticketIds());
        List<Ticket> undoTickets = ticketRepository.findByBookingIdOrderBySeatIdAsc(lockedBooking.getId()).stream()
                .filter(ticket -> requestedTicketIds.contains(ticket.getId()) && ticket.isCheckedIn())
                .toList();
        for (Ticket ticket : undoTickets) {
            ticket.setCheckedIn(false);
            ticket.setCheckedInAt(null);
        }
        ticketRepository.saveAll(undoTickets);
        writeLog(staffUserId, ticketUndoRequest.cinemaId(), lockedBooking, code, TicketScanAction.UNDO,
                CheckInResultCode.UNDONE, undoTickets.size());
        auditLogService.record(staffUserId, ticketUndoRequest.cinemaId(), AuditAction.UNDO_CHECK_IN, "BOOKING",
                lockedBooking.getBookingCode(), "Hoàn tác " + undoTickets.size() + " ghế đã vào rạp");
        TicketScanResponse refreshedResponse = evaluate(staffUserId, ticketUndoRequest.cinemaId(), lockedBooking);
        return new TicketScanResponse(
                CheckInResultCode.UNDONE,
                "Đã hoàn tác " + undoTickets.size() + " ghế.",
                refreshedResponse.canCheckIn(),
                refreshedResponse.pendingTicketCount(),
                refreshedResponse.booking());
    }

    @Transactional(readOnly = true)
    @Override
    public List<TicketScanLogResponse> findHistory(Long staffUserId, Long cinemaId) {
        assertStaffCanWorkAt(staffUserId, cinemaId);
        List<TicketScanLog> ticketScanLogs =
                ticketScanLogRepository.findByCinemaIdAndResultCodeInOrderByIdDesc(
                        cinemaId, HISTORY_RESULT_CODES, PageRequest.of(0, HISTORY_LIMIT));
        Map<Long, String> bookingCodes = bookingRepository.findAllById(
                        ticketScanLogs.stream().map(TicketScanLog::getBookingId).filter(Objects::nonNull)
                                .distinct().toList()).stream()
                .collect(Collectors.toMap(Booking::getId, Booking::getBookingCode));
        Map<Long, String> staffNames = new HashMap<>();
        for (Long loggedStaffUserId : ticketScanLogs.stream().map(TicketScanLog::getStaffUserId).distinct().toList()) {
            staffNames.put(loggedStaffUserId, userQueryService.findContact(loggedStaffUserId)
                    .map(UserContactResponse::fullName)
                    .orElse("Nhân viên #" + loggedStaffUserId));
        }
        return ticketScanLogs.stream()
                .map(ticketScanLog -> new TicketScanLogResponse(
                        ticketScanLog.getId(),
                        ticketScanLog.getBookingId() == null ? ticketScanLog.getScannedCode()
                                : bookingCodes.getOrDefault(ticketScanLog.getBookingId(), ticketScanLog.getScannedCode()),
                        ticketScanLog.getAction(),
                        ticketScanLog.getResultCode(),
                        ticketScanLog.getTicketCount(),
                        staffNames.get(ticketScanLog.getStaffUserId()),
                        ticketScanLog.getCreatedAt()))
                .toList();
    }

    private TicketScanResponse evaluate(Long staffUserId, Long workingCinemaId, Booking booking) {
        Optional<BookingDetailResponse> optionalBookingDetail = bookingDetailService.findBookingDetail(booking.getId());
        if (optionalBookingDetail.isEmpty()) {
            return notFound();
        }
        BookingDetailResponse bookingDetail = optionalBookingDetail.get();
        ScanBookingResponse scanBooking = toScanBooking(bookingDetail);
        int pendingTicketCount = (int) bookingDetail.tickets().stream()
                .filter(bookingTicket -> !bookingTicket.checkedIn())
                .count();
        BookingShowtimeResponse showtime = bookingDetail.showtime();

        if (!showtime.cinemaId().equals(workingCinemaId) || !branchAccessService.canAccessCinema(staffUserId,
                showtime.cinemaId())) {
            return blocked(CheckInResultCode.WRONG_BRANCH,
                    "Vé thuộc " + showtime.cinemaName() + ", không phải chi nhánh đang làm việc.",
                    pendingTicketCount, scanBooking);
        }
        BookingStatus status = booking.getStatus();
        if (status == BookingStatus.CANCELLED) {
            return blocked(CheckInResultCode.CANCELLED, "Đơn đặt vé này đã bị hủy.", pendingTicketCount, scanBooking);
        }
        if (status == BookingStatus.REFUND_REQUIRED || status == BookingStatus.REFUNDED) {
            return blocked(CheckInResultCode.REFUNDED, "Đơn đặt vé này đã hoàn tiền hoặc đang chờ hoàn tiền.",
                    pendingTicketCount, scanBooking);
        }
        if (status != BookingStatus.PAID) {
            return blocked(CheckInResultCode.NOT_PAID, "Đơn đặt vé này chưa thanh toán.", pendingTicketCount,
                    scanBooking);
        }
        if (bookingDetail.tickets().isEmpty() || pendingTicketCount == 0) {
            return blocked(CheckInResultCode.ALREADY_USED, "Tất cả ghế của đơn này đã vào rạp.", 0, scanBooking);
        }
        Instant now = Instant.now(clock);
        Instant earliestEntry = showtime.startTime().minus(EARLY_ENTRY_WINDOW);
        if (now.isBefore(earliestEntry)) {
            return blocked(CheckInResultCode.NOT_YET_TIME,
                    "Chưa tới giờ vào rạp. Cho phép soát vé từ " + formatTime(earliestEntry) + ".",
                    pendingTicketCount, scanBooking);
        }
        if (now.isAfter(showtime.endTime())) {
            return blocked(CheckInResultCode.EXPIRED,
                    "Suất chiếu đã kết thúc lúc " + formatTime(showtime.endTime()) + ".", pendingTicketCount,
                    scanBooking);
        }
        int totalTicketCount = bookingDetail.tickets().size();
        String message = pendingTicketCount == totalTicketCount
                ? "Vé hợp lệ, cho khách vào rạp."
                : "Vé hợp lệ. Còn " + pendingTicketCount + "/" + totalTicketCount + " ghế chưa vào.";
        return new TicketScanResponse(CheckInResultCode.VALID, message, true, pendingTicketCount, scanBooking);
    }

    private TicketScanResponse blocked(
            CheckInResultCode checkInResultCode, String message, int pendingTicketCount, ScanBookingResponse booking) {
        return new TicketScanResponse(checkInResultCode, message, false, pendingTicketCount, booking);
    }

    private TicketScanResponse notFound() {
        return new TicketScanResponse(CheckInResultCode.NOT_FOUND, "Không tìm thấy vé với mã này.", false, 0, null);
    }

    private ScanBookingResponse toScanBooking(BookingDetailResponse bookingDetail) {
        BookingShowtimeResponse showtime = bookingDetail.showtime();
        Map<Long, BookingSeatResponse> bookingSeatsBySeatId = bookingDetail.seats().stream()
                .collect(Collectors.toMap(BookingSeatResponse::seatId, Function.identity(), (first, second) -> first));
        List<ScanTicketResponse> scanTicketResponses = bookingDetail.tickets().stream()
                .map(bookingTicket -> toScanTicket(bookingTicket, bookingSeatsBySeatId))
                .toList();
        return new ScanBookingResponse(
                bookingDetail.bookingId(),
                bookingDetail.bookingCode(),
                bookingDetail.status(),
                bookingDetail.createdAt(),
                paymentQueryService.findSuccessfulPaymentMethod(bookingDetail.bookingId()).orElse(null),
                showtime.movieTitle(),
                showtime.posterUrl(),
                showtime.rated(),
                showtime.cinemaName(),
                showtime.hallName(),
                showtime.screenType(),
                showtime.startTime(),
                showtime.endTime(),
                scanTicketResponses,
                toScanFnbLines(bookingDetail.fnbLines()),
                bookingDetail.finalAmount());
    }

    private List<ScanFnbLineResponse> toScanFnbLines(List<BookingFnbLineResponse> bookingFnbLineResponses) {
        Map<Long, String> compositions = fnbQueryService.findComboCompositions(bookingFnbLineResponses.stream()
                .map(BookingFnbLineResponse::comboId)
                .filter(Objects::nonNull)
                .toList());
        return bookingFnbLineResponses.stream()
                .map(bookingFnbLineResponse -> new ScanFnbLineResponse(
                        bookingFnbLineResponse.name(),
                        bookingFnbLineResponse.imageUrl(),
                        bookingFnbLineResponse.comboId() == null
                                ? null
                                : compositions.get(bookingFnbLineResponse.comboId()),
                        bookingFnbLineResponse.quantity(),
                        bookingFnbLineResponse.unitPrice(),
                        bookingFnbLineResponse.lineAmount()))
                .toList();
    }

    private ScanTicketResponse toScanTicket(
            BookingTicketResponse bookingTicket, Map<Long, BookingSeatResponse> bookingSeatsBySeatId) {
        BookingSeatResponse bookingSeat = bookingSeatsBySeatId.get(bookingTicket.seatId());
        return new ScanTicketResponse(
                bookingTicket.ticketId(),
                bookingTicket.seatLabel(),
                bookingSeat == null ? null : bookingSeat.seatTypeName(),
                bookingSeat == null ? null : bookingSeat.seatTypeCode(),
                bookingTicket.price(),
                bookingTicket.checkedIn(),
                bookingTicket.checkedInAt());
    }

    private List<Ticket> selectTicketsToCheckIn(List<Ticket> tickets, List<Long> requestedTicketIds) {
        List<Ticket> uncheckedTickets = tickets.stream().filter(ticket -> !ticket.isCheckedIn()).toList();
        if (requestedTicketIds == null || requestedTicketIds.isEmpty()) {
            return uncheckedTickets;
        }
        Set<Long> ownTicketIds = tickets.stream().map(Ticket::getId).collect(Collectors.toSet());
        if (!ownTicketIds.containsAll(requestedTicketIds)) {
            throw new AppException("Ghế được chọn không thuộc đơn này", HttpStatus.BAD_REQUEST, "INVALID_TICKET");
        }
        Set<Long> requestedTicketIdSet = Set.copyOf(requestedTicketIds);
        return uncheckedTickets.stream().filter(ticket -> requestedTicketIdSet.contains(ticket.getId())).toList();
    }

    private Optional<Booking> findBooking(String code) {
        Optional<Booking> bookingByQrCode = bookingRepository.findByQrCode(code);
        if (bookingByQrCode.isPresent()) {
            return bookingByQrCode;
        }
        return bookingRepository.findByBookingCode(code);
    }

    private void assertStaffCanWorkAt(Long staffUserId, Long cinemaId) {
        branchAccessService.assertCanAccessCinema(staffUserId, cinemaId);
    }

    private void writeLog(
            Long staffUserId,
            Long cinemaId,
            Booking booking,
            String scannedCode,
            TicketScanAction ticketScanAction,
            CheckInResultCode checkInResultCode,
            int ticketCount) {
        TicketScanLog ticketScanLog = new TicketScanLog();
        ticketScanLog.setBookingId(booking == null ? null : booking.getId());
        ticketScanLog.setCinemaId(cinemaId);
        ticketScanLog.setStaffUserId(staffUserId);
        ticketScanLog.setScannedCode(scannedCode);
        ticketScanLog.setAction(ticketScanAction);
        ticketScanLog.setResultCode(checkInResultCode);
        ticketScanLog.setTicketCount(ticketCount);
        ticketScanLog.setCreatedAt(Instant.now(clock));
        ticketScanLogRepository.save(ticketScanLog);
    }

    private static String normalizeCode(String code) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        return normalizedCode.length() > 100 ? normalizedCode.substring(0, 100) : normalizedCode;
    }

    private static String formatTime(Instant instant) {
        return TIME_FORMATTER.format(instant.atZone(VIETNAM_ZONE));
    }
}
