package com.cinema.modules.booking.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.dto.response.UserContactResponse;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.auth.service.interfaces.IUserQueryService;
import com.cinema.modules.booking.dto.request.FnbPickupDeliverLineRequest;
import com.cinema.modules.booking.dto.request.FnbPickupDeliverRequest;
import com.cinema.modules.booking.dto.request.FnbPickupScanRequest;
import com.cinema.modules.booking.dto.response.BookingDetailResponse;
import com.cinema.modules.booking.dto.response.BookingShowtimeResponse;
import com.cinema.modules.booking.dto.response.FnbDeliveryLogResponse;
import com.cinema.modules.booking.dto.response.FnbPickupBookingResponse;
import com.cinema.modules.booking.dto.response.FnbPickupLineResponse;
import com.cinema.modules.booking.dto.response.FnbPickupQueueBookingResponse;
import com.cinema.modules.booking.dto.response.FnbPickupQueueLineResponse;
import com.cinema.modules.booking.dto.response.FnbPickupQueueResponse;
import com.cinema.modules.booking.dto.response.FnbPickupScanResponse;
import com.cinema.modules.booking.dto.response.FnbPickupShowtimeGroupResponse;
import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.entity.BookingFnbDetail;
import com.cinema.modules.booking.entity.FnbDeliveryLog;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.enums.FnbPickupResultCode;
import com.cinema.modules.booking.repository.BookingFnbDetailRepository;
import com.cinema.modules.booking.repository.BookingRepository;
import com.cinema.modules.booking.repository.FnbDeliveryLogRepository;
import com.cinema.modules.booking.service.interfaces.IBookingDetailService;
import com.cinema.modules.booking.service.interfaces.IFnbPickupService;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.fnb.dto.response.FnbProductInfoResponse;
import com.cinema.modules.fnb.service.interfaces.IFnbQueryService;
import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import com.cinema.modules.showtime.dto.response.ShowtimeInfoResponse;
import com.cinema.modules.showtime.service.interfaces.IShowtimeQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FnbPickupService implements IFnbPickupService {

    private static final String SCAN_CHANNEL = "FNB_PICKUP";
    static final Duration EARLY_PICKUP_WINDOW = Duration.ofMinutes(60);
    private static final int HISTORY_LIMIT = 50;
    private static final Duration QUEUE_HORIZON = Duration.ofHours(24);
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final BookingRepository bookingRepository;
    private final BookingFnbDetailRepository bookingFnbDetailRepository;
    private final FnbDeliveryLogRepository fnbDeliveryLogRepository;
    private final IBookingDetailService bookingDetailService;
    private final IBranchAccessService branchAccessService;
    private final IUserQueryService userQueryService;
    private final IFnbQueryService fnbQueryService;
    private final IShowtimeQueryService showtimeQueryService;
    private final IMovieQueryService movieQueryService;
    private final ICinemaQueryService cinemaQueryService;
    private final ScanGuard scanGuard;
    private final Clock clock;

    public FnbPickupService(
            BookingRepository bookingRepository,
            BookingFnbDetailRepository bookingFnbDetailRepository,
            FnbDeliveryLogRepository fnbDeliveryLogRepository,
            IBookingDetailService bookingDetailService,
            IBranchAccessService branchAccessService,
            IUserQueryService userQueryService,
            IFnbQueryService fnbQueryService,
            IShowtimeQueryService showtimeQueryService,
            IMovieQueryService movieQueryService,
            ICinemaQueryService cinemaQueryService,
            ScanGuard scanGuard,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.bookingFnbDetailRepository = bookingFnbDetailRepository;
        this.fnbDeliveryLogRepository = fnbDeliveryLogRepository;
        this.bookingDetailService = bookingDetailService;
        this.branchAccessService = branchAccessService;
        this.userQueryService = userQueryService;
        this.fnbQueryService = fnbQueryService;
        this.showtimeQueryService = showtimeQueryService;
        this.movieQueryService = movieQueryService;
        this.cinemaQueryService = cinemaQueryService;
        this.scanGuard = scanGuard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public FnbPickupScanResponse scan(Long staffUserId, FnbPickupScanRequest fnbPickupScanRequest) {
        branchAccessService.assertCanAccessCinema(staffUserId, fnbPickupScanRequest.cinemaId());
        scanGuard.assertScanAllowed(staffUserId, fnbPickupScanRequest.cinemaId(), SCAN_CHANNEL);
        String code = normalizeCode(fnbPickupScanRequest.code());
        Optional<Booking> optionalBooking = findBooking(code);
        if (optionalBooking.isEmpty()) {
            scanGuard.registerUnknownCode(staffUserId, fnbPickupScanRequest.cinemaId(), SCAN_CHANNEL, code);
        }
        return optionalBooking
                .map(booking -> evaluate(staffUserId, fnbPickupScanRequest.cinemaId(), booking))
                .orElseGet(this::notFound);
    }

    @Transactional
    @Override
    public FnbPickupScanResponse deliver(Long staffUserId, FnbPickupDeliverRequest fnbPickupDeliverRequest) {
        branchAccessService.assertCanAccessCinema(staffUserId, fnbPickupDeliverRequest.cinemaId());
        scanGuard.assertScanAllowed(staffUserId, fnbPickupDeliverRequest.cinemaId(), SCAN_CHANNEL);
        String code = normalizeCode(fnbPickupDeliverRequest.code());
        Booking booking = findBooking(code)
                .orElseThrow(() -> new AppException("Không tìm thấy đơn đặt vé", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        Booking lockedBooking = bookingRepository.findByIdForUpdate(booking.getId())
                .orElseThrow(() -> new AppException("Không tìm thấy đơn đặt vé", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        FnbPickupScanResponse currentResponse =
                evaluate(staffUserId, fnbPickupDeliverRequest.cinemaId(), lockedBooking);
        if (!currentResponse.canDeliver()) {
            return currentResponse;
        }
        List<BookingFnbDetail> bookingFnbDetails =
                bookingFnbDetailRepository.findByBookingIdOrderByIdAsc(lockedBooking.getId());
        FnbProductLookup fnbProductLookup = lookupProducts(bookingFnbDetails);
        Map<Long, Integer> deliverQuantities =
                resolveDeliverQuantities(bookingFnbDetails, fnbPickupDeliverRequest.lines(), fnbProductLookup);
        Instant now = Instant.now(clock);
        int deliveredTotal = 0;
        List<FnbDeliveryLog> fnbDeliveryLogs = new ArrayList<>();
        for (BookingFnbDetail bookingFnbDetail : bookingFnbDetails) {
            int deliverQuantity = deliverQuantities.getOrDefault(bookingFnbDetail.getId(), 0);
            if (deliverQuantity > 0) {
                bookingFnbDetail.setDeliveredQuantity(bookingFnbDetail.getDeliveredQuantity() + deliverQuantity);
                bookingFnbDetail.setDeliveredAt(now);
                bookingFnbDetail.setDeliveredByUserId(staffUserId);
                deliveredTotal += deliverQuantity;
                fnbDeliveryLogs.add(buildDeliveryLog(
                        staffUserId, fnbPickupDeliverRequest.cinemaId(), lockedBooking, bookingFnbDetail,
                        fnbProductLookup.name(bookingFnbDetail), deliverQuantity, now));
            }
        }
        bookingFnbDetailRepository.saveAll(bookingFnbDetails);
        fnbDeliveryLogRepository.saveAll(fnbDeliveryLogs);
        FnbPickupBookingResponse refreshedBooking =
                toPickupBooking(bookingDetailService.findBookingDetail(lockedBooking.getId()).orElseThrow(),
                        bookingFnbDetails, fnbProductLookup);
        String message = refreshedBooking.remainingQuantity() == 0
                ? "Đã giao đủ " + deliveredTotal + " phần, đơn hoàn tất."
                : "Đã giao " + deliveredTotal + " phần. Còn " + refreshedBooking.remainingQuantity()
                        + " phần chưa giao.";
        return new FnbPickupScanResponse(
                FnbPickupResultCode.DELIVERED, message, refreshedBooking.remainingQuantity() > 0, refreshedBooking);
    }

    @Transactional(readOnly = true)
    @Override
    public FnbPickupQueueResponse findQueue(Long staffUserId, Long cinemaId) {
        branchAccessService.assertCanAccessCinema(staffUserId, cinemaId);
        Instant now = Instant.now(clock);
        List<ShowtimeInfoResponse> showtimeInfoResponses =
                showtimeQueryService.findActiveShowtimeInfosBetween(now, now.plus(QUEUE_HORIZON));
        if (showtimeInfoResponses.isEmpty()) {
            return new FnbPickupQueueResponse(now, List.of());
        }
        List<Booking> bookings = bookingRepository.findWithUndeliveredFnb(
                cinemaId, BookingStatus.PAID, showtimeInfoResponses.stream().map(ShowtimeInfoResponse::id).toList());
        if (bookings.isEmpty()) {
            return new FnbPickupQueueResponse(now, List.of());
        }
        Map<Long, List<BookingFnbDetail>> detailsByBookingId = bookingFnbDetailRepository
                .findByBookingIdInOrderByIdAsc(bookings.stream().map(Booking::getId).toList()).stream()
                .collect(Collectors.groupingBy(BookingFnbDetail::getBookingId));
        FnbProductLookup fnbProductLookup = lookupProducts(
                detailsByBookingId.values().stream().flatMap(List::stream).toList());
        Map<Long, List<Booking>> bookingsByShowtimeId =
                bookings.stream().collect(Collectors.groupingBy(Booking::getShowtimeId));
        List<ShowtimeInfoResponse> activeShowtimeInfoResponses = showtimeInfoResponses.stream()
                .filter(showtimeInfoResponse -> bookingsByShowtimeId.containsKey(showtimeInfoResponse.id()))
                .toList();
        Map<Long, MovieInfoResponse> movieInfoResponses = movieQueryService.findMovieInfos(
                activeShowtimeInfoResponses.stream().map(ShowtimeInfoResponse::movieId).distinct().toList());
        Map<Long, HallInfoResponse> hallInfoResponses = cinemaQueryService.findHallInfos(
                activeShowtimeInfoResponses.stream().map(ShowtimeInfoResponse::hallId).distinct().toList());
        List<FnbPickupShowtimeGroupResponse> groups = new ArrayList<>();
        for (ShowtimeInfoResponse showtimeInfoResponse : activeShowtimeInfoResponses) {
            List<FnbPickupQueueBookingResponse> queueBookings = bookingsByShowtimeId.get(showtimeInfoResponse.id())
                    .stream()
                    .map(booking -> toQueueBooking(
                            booking, detailsByBookingId.getOrDefault(booking.getId(), List.of()), fnbProductLookup))
                    .toList();
            Instant pickupOpensAt = showtimeInfoResponse.startTime().minus(EARLY_PICKUP_WINDOW);
            MovieInfoResponse movieInfoResponse = movieInfoResponses.get(showtimeInfoResponse.movieId());
            HallInfoResponse hallInfoResponse = hallInfoResponses.get(showtimeInfoResponse.hallId());
            groups.add(new FnbPickupShowtimeGroupResponse(
                    showtimeInfoResponse.id(),
                    movieInfoResponse == null ? "Phim #" + showtimeInfoResponse.movieId() : movieInfoResponse.title(),
                    hallInfoResponse == null ? "Phòng #" + showtimeInfoResponse.hallId() : hallInfoResponse.name(),
                    showtimeInfoResponse.startTime(),
                    showtimeInfoResponse.endTime(),
                    pickupOpensAt,
                    !now.isBefore(pickupOpensAt),
                    queueBookings.size(),
                    queueBookings.stream().mapToInt(FnbPickupQueueBookingResponse::remainingQuantity).sum(),
                    queueBookings));
        }
        return new FnbPickupQueueResponse(now, groups);
    }

    @Transactional(readOnly = true)
    @Override
    public List<FnbDeliveryLogResponse> findHistory(Long staffUserId, Long cinemaId) {
        branchAccessService.assertCanAccessCinema(staffUserId, cinemaId);
        List<FnbDeliveryLog> fnbDeliveryLogs =
                fnbDeliveryLogRepository.findByCinemaIdOrderByIdDesc(cinemaId, PageRequest.of(0, HISTORY_LIMIT));
        Map<Long, String> staffNames = new HashMap<>();
        for (Long loggedStaffUserId :
                fnbDeliveryLogs.stream().map(FnbDeliveryLog::getStaffUserId).distinct().toList()) {
            staffNames.put(loggedStaffUserId, userQueryService.findContact(loggedStaffUserId)
                    .map(UserContactResponse::fullName)
                    .orElse("Nhân viên #" + loggedStaffUserId));
        }
        return fnbDeliveryLogs.stream()
                .map(fnbDeliveryLog -> new FnbDeliveryLogResponse(
                        fnbDeliveryLog.getId(),
                        fnbDeliveryLog.getBookingCode(),
                        fnbDeliveryLog.getProductName(),
                        fnbDeliveryLog.getQuantity(),
                        staffNames.get(fnbDeliveryLog.getStaffUserId()),
                        fnbDeliveryLog.getCreatedAt()))
                .toList();
    }

    private FnbDeliveryLog buildDeliveryLog(
            Long staffUserId,
            Long cinemaId,
            Booking booking,
            BookingFnbDetail bookingFnbDetail,
            String productName,
            int quantity,
            Instant createdAt) {
        FnbDeliveryLog fnbDeliveryLog = new FnbDeliveryLog();
        fnbDeliveryLog.setBookingId(booking.getId());
        fnbDeliveryLog.setBookingFnbDetailId(bookingFnbDetail.getId());
        fnbDeliveryLog.setCinemaId(cinemaId);
        fnbDeliveryLog.setStaffUserId(staffUserId);
        fnbDeliveryLog.setBookingCode(booking.getBookingCode());
        fnbDeliveryLog.setProductName(productName);
        fnbDeliveryLog.setQuantity(quantity);
        fnbDeliveryLog.setCreatedAt(createdAt);
        return fnbDeliveryLog;
    }

    private FnbPickupScanResponse evaluate(Long staffUserId, Long workingCinemaId, Booking booking) {
        Optional<BookingDetailResponse> optionalBookingDetail = bookingDetailService.findBookingDetail(booking.getId());
        if (optionalBookingDetail.isEmpty()) {
            return notFound();
        }
        BookingDetailResponse bookingDetail = optionalBookingDetail.get();
        List<BookingFnbDetail> bookingFnbDetails =
                bookingFnbDetailRepository.findByBookingIdOrderByIdAsc(booking.getId());
        FnbPickupBookingResponse pickupBooking =
                toPickupBooking(bookingDetail, bookingFnbDetails, lookupProducts(bookingFnbDetails));
        BookingShowtimeResponse showtime = bookingDetail.showtime();
        if (!showtime.cinemaId().equals(workingCinemaId)
                || !branchAccessService.canAccessCinema(staffUserId, showtime.cinemaId())) {
            return blocked(FnbPickupResultCode.WRONG_BRANCH,
                    "Đơn thuộc " + showtime.cinemaName() + ", không phải chi nhánh đang làm việc.", pickupBooking);
        }
        BookingStatus status = booking.getStatus();
        if (status == BookingStatus.CANCELLED) {
            return blocked(FnbPickupResultCode.CANCELLED, "Đơn đặt vé này đã bị hủy.", pickupBooking);
        }
        if (status == BookingStatus.REFUND_REQUIRED || status == BookingStatus.REFUNDED) {
            return blocked(FnbPickupResultCode.REFUNDED, "Đơn đặt vé này đã hoàn tiền hoặc đang chờ hoàn tiền.",
                    pickupBooking);
        }
        if (status != BookingStatus.PAID) {
            return blocked(FnbPickupResultCode.NOT_PAID, "Đơn đặt vé này chưa thanh toán.", pickupBooking);
        }
        if (bookingFnbDetails.isEmpty()) {
            return blocked(FnbPickupResultCode.NO_FNB, "Đơn này không có bắp nước.", pickupBooking);
        }
        if (pickupBooking.remainingQuantity() == 0) {
            return blocked(FnbPickupResultCode.ALL_DELIVERED, "Đơn này đã nhận đủ bắp nước.", pickupBooking);
        }
        Instant now = Instant.now(clock);
        Instant earliestPickup = showtime.startTime().minus(EARLY_PICKUP_WINDOW);
        if (now.isBefore(earliestPickup)) {
            return blocked(FnbPickupResultCode.NOT_YET_TIME,
                    "Chưa tới giờ nhận bắp nước. Cho phép giao từ " + formatTime(earliestPickup) + ".", pickupBooking);
        }
        if (now.isAfter(showtime.endTime())) {
            return blocked(FnbPickupResultCode.EXPIRED,
                    "Suất chiếu đã kết thúc lúc " + formatTime(showtime.endTime()) + ".", pickupBooking);
        }
        return new FnbPickupScanResponse(
                FnbPickupResultCode.VALID,
                "Còn " + pickupBooking.remainingQuantity() + "/" + pickupBooking.totalQuantity()
                        + " phần chưa giao.",
                true,
                pickupBooking);
    }

    private Map<Long, Integer> resolveDeliverQuantities(
            List<BookingFnbDetail> bookingFnbDetails,
            List<FnbPickupDeliverLineRequest> fnbPickupDeliverLineRequests,
            FnbProductLookup fnbProductLookup) {
        Map<Long, BookingFnbDetail> bookingFnbDetailsById =
                bookingFnbDetails.stream().collect(Collectors.toMap(BookingFnbDetail::getId, Function.identity()));
        Map<Long, Integer> deliverQuantities = new HashMap<>();
        if (fnbPickupDeliverLineRequests == null || fnbPickupDeliverLineRequests.isEmpty()) {
            for (BookingFnbDetail bookingFnbDetail : bookingFnbDetails) {
                deliverQuantities.put(bookingFnbDetail.getId(), remainingQuantity(bookingFnbDetail));
            }
            return deliverQuantities;
        }
        for (FnbPickupDeliverLineRequest fnbPickupDeliverLineRequest : fnbPickupDeliverLineRequests) {
            BookingFnbDetail bookingFnbDetail = bookingFnbDetailsById.get(fnbPickupDeliverLineRequest.detailId());
            if (bookingFnbDetail == null) {
                throw new AppException("Dòng bắp nước không thuộc đơn này", HttpStatus.BAD_REQUEST,
                        "INVALID_FNB_LINE");
            }
            if (deliverQuantities.containsKey(bookingFnbDetail.getId())) {
                throw new AppException("Mỗi dòng bắp nước chỉ được chọn một lần", HttpStatus.BAD_REQUEST,
                        "DUPLICATE_FNB_LINE");
            }
            int remainingQuantity = remainingQuantity(bookingFnbDetail);
            if (fnbPickupDeliverLineRequest.quantity() > remainingQuantity) {
                throw new AppException(
                        "\"" + fnbProductLookup.name(bookingFnbDetail) + "\" chỉ còn " + remainingQuantity
                                + " phần chưa giao. Vui lòng quét lại để cập nhật.",
                        HttpStatus.CONFLICT,
                        "FNB_QUANTITY_EXCEEDED");
            }
            deliverQuantities.put(bookingFnbDetail.getId(), fnbPickupDeliverLineRequest.quantity());
        }
        return deliverQuantities;
    }

    private FnbPickupBookingResponse toPickupBooking(
            BookingDetailResponse bookingDetail,
            List<BookingFnbDetail> bookingFnbDetails,
            FnbProductLookup fnbProductLookup) {
        Map<Long, String> compositions = fnbQueryService.findComboCompositions(bookingFnbDetails.stream()
                .map(BookingFnbDetail::getComboId)
                .filter(Objects::nonNull)
                .toList());
        Map<Long, String> staffNames = new HashMap<>();
        for (Long deliveredByUserId : bookingFnbDetails.stream()
                .map(BookingFnbDetail::getDeliveredByUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList()) {
            staffNames.put(deliveredByUserId, userQueryService.findContact(deliveredByUserId)
                    .map(UserContactResponse::fullName)
                    .orElse("Nhân viên #" + deliveredByUserId));
        }
        List<FnbPickupLineResponse> fnbPickupLineResponses = bookingFnbDetails.stream()
                .map(bookingFnbDetail -> new FnbPickupLineResponse(
                        bookingFnbDetail.getId(),
                        fnbProductLookup.name(bookingFnbDetail),
                        fnbProductLookup.imageUrl(bookingFnbDetail),
                        bookingFnbDetail.getComboId() == null ? null : compositions.get(bookingFnbDetail.getComboId()),
                        bookingFnbDetail.getQuantity(),
                        bookingFnbDetail.getDeliveredQuantity(),
                        remainingQuantity(bookingFnbDetail),
                        bookingFnbDetail.getUnitPrice(),
                        bookingFnbDetail.getDeliveredAt(),
                        bookingFnbDetail.getDeliveredByUserId() == null
                                ? null
                                : staffNames.get(bookingFnbDetail.getDeliveredByUserId())))
                .toList();
        BookingShowtimeResponse showtime = bookingDetail.showtime();
        return new FnbPickupBookingResponse(
                bookingDetail.bookingId(),
                bookingDetail.bookingCode(),
                showtime.movieTitle(),
                showtime.posterUrl(),
                showtime.cinemaName(),
                showtime.hallName(),
                showtime.startTime(),
                showtime.endTime(),
                fnbPickupLineResponses,
                fnbPickupLineResponses.stream().mapToInt(FnbPickupLineResponse::quantity).sum(),
                fnbPickupLineResponses.stream().mapToInt(FnbPickupLineResponse::remainingQuantity).sum());
    }

    private FnbPickupQueueBookingResponse toQueueBooking(
            Booking booking, List<BookingFnbDetail> bookingFnbDetails, FnbProductLookup fnbProductLookup) {
        Map<String, Integer> remainingByName = new LinkedHashMap<>();
        for (BookingFnbDetail bookingFnbDetail : bookingFnbDetails) {
            int remainingQuantity = remainingQuantity(bookingFnbDetail);
            if (remainingQuantity > 0) {
                remainingByName.merge(fnbProductLookup.name(bookingFnbDetail), remainingQuantity, Integer::sum);
            }
        }
        List<FnbPickupQueueLineResponse> fnbPickupQueueLineResponses = remainingByName.entrySet().stream()
                .map(entry -> new FnbPickupQueueLineResponse(entry.getKey(), entry.getValue()))
                .toList();
        return new FnbPickupQueueBookingResponse(
                booking.getBookingCode(),
                fnbPickupQueueLineResponses.stream().mapToInt(FnbPickupQueueLineResponse::remainingQuantity).sum(),
                fnbPickupQueueLineResponses);
    }

    private FnbProductLookup lookupProducts(List<BookingFnbDetail> bookingFnbDetails) {
        return new FnbProductLookup(
                fnbQueryService.findComboInfos(bookingFnbDetails.stream()
                        .map(BookingFnbDetail::getComboId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()),
                fnbQueryService.findItemInfos(bookingFnbDetails.stream()
                        .map(BookingFnbDetail::getItemId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()));
    }

    private FnbPickupScanResponse blocked(
            FnbPickupResultCode fnbPickupResultCode, String message, FnbPickupBookingResponse booking) {
        return new FnbPickupScanResponse(fnbPickupResultCode, message, false, booking);
    }

    private FnbPickupScanResponse notFound() {
        return new FnbPickupScanResponse(FnbPickupResultCode.NOT_FOUND, "Không tìm thấy vé với mã này.", false, null);
    }

    private Optional<Booking> findBooking(String code) {
        Optional<Booking> bookingByQrCode = bookingRepository.findByQrCode(code);
        if (bookingByQrCode.isPresent()) {
            return bookingByQrCode;
        }
        return bookingRepository.findByBookingCode(code);
    }

    private static int remainingQuantity(BookingFnbDetail bookingFnbDetail) {
        return bookingFnbDetail.getQuantity() - bookingFnbDetail.getDeliveredQuantity();
    }

    private static String normalizeCode(String code) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        return normalizedCode.length() > 100 ? normalizedCode.substring(0, 100) : normalizedCode;
    }

    private static String formatTime(Instant instant) {
        return TIME_FORMATTER.format(instant.atZone(VIETNAM_ZONE));
    }

    private record FnbProductLookup(
            Map<Long, FnbProductInfoResponse> comboInfos, Map<Long, FnbProductInfoResponse> itemInfos) {

        String name(BookingFnbDetail bookingFnbDetail) {
            FnbProductInfoResponse fnbProductInfoResponse = find(bookingFnbDetail);
            return fnbProductInfoResponse == null ? "Món không còn trong thực đơn" : fnbProductInfoResponse.name();
        }

        String imageUrl(BookingFnbDetail bookingFnbDetail) {
            FnbProductInfoResponse fnbProductInfoResponse = find(bookingFnbDetail);
            return fnbProductInfoResponse == null ? null : fnbProductInfoResponse.imageUrl();
        }

        private FnbProductInfoResponse find(BookingFnbDetail bookingFnbDetail) {
            return bookingFnbDetail.getComboId() != null
                    ? comboInfos.get(bookingFnbDetail.getComboId())
                    : itemInfos.get(bookingFnbDetail.getItemId());
        }
    }
}
