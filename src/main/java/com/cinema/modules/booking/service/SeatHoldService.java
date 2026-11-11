package com.cinema.modules.booking.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.util.BookingWindow;
import com.cinema.config.properties.BookingPolicyProperties;
import com.cinema.modules.booking.BookingRules;
import com.cinema.modules.booking.dto.message.SeatStateChange;
import com.cinema.modules.booking.dto.response.HeldSeatResponse;
import com.cinema.modules.booking.dto.response.SeatHoldResponse;
import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.entity.SeatReservation;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.enums.SeatAvailability;
import com.cinema.modules.booking.enums.SeatReservationStatus;
import com.cinema.modules.booking.repository.BookingRepository;
import com.cinema.modules.booking.repository.SeatReservationRepository;
import com.cinema.modules.booking.service.interfaces.ISeatHoldService;
import com.cinema.modules.booking.service.interfaces.ISeatStatusBroadcaster;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.dto.response.SeatPricingInfoResponse;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.showtime.dto.response.SeatTypePriceResponse;
import com.cinema.modules.showtime.dto.response.ShowtimeInfoResponse;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import com.cinema.modules.showtime.service.interfaces.IDynamicPricingService;
import com.cinema.modules.showtime.service.interfaces.IShowtimeQueryService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class SeatHoldService implements ISeatHoldService {

    private static final String SEAT_LOCK_KEY_TEMPLATE = "lock:showtime:%d:seat:%d";
    private static final long LOCK_WAIT_MILLISECONDS = 500;
    private static final long LOCK_LEASE_MILLISECONDS = 10_000;
    private static final String SEAT_UNIQUE_CONSTRAINT = "uq_showtime_seat";

    private final BookingRepository bookingRepository;
    private final SeatReservationRepository seatReservationRepository;
    private final IShowtimeQueryService showtimeQueryService;
    private final IDynamicPricingService dynamicPricingService;
    private final ICinemaQueryService cinemaQueryService;
    private final ISeatStatusBroadcaster seatStatusBroadcaster;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;
    private final BookingPolicyProperties bookingPolicyProperties;
    private final Clock clock;

    public SeatHoldService(
            BookingRepository bookingRepository,
            SeatReservationRepository seatReservationRepository,
            IShowtimeQueryService showtimeQueryService,
            IDynamicPricingService dynamicPricingService,
            ICinemaQueryService cinemaQueryService,
            ISeatStatusBroadcaster seatStatusBroadcaster,
            RedissonClient redissonClient,
            TransactionTemplate transactionTemplate,
            BookingPolicyProperties bookingPolicyProperties,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.seatReservationRepository = seatReservationRepository;
        this.showtimeQueryService = showtimeQueryService;
        this.dynamicPricingService = dynamicPricingService;
        this.cinemaQueryService = cinemaQueryService;
        this.seatStatusBroadcaster = seatStatusBroadcaster;
        this.redissonClient = redissonClient;
        this.transactionTemplate = transactionTemplate;
        this.bookingPolicyProperties = bookingPolicyProperties;
        this.clock = clock;
    }

    @Override
    public SeatHoldResponse holdSeat(Long userId, Long showtimeId, Long seatId) {
        Instant now = Instant.now(clock);
        ShowtimeInfoResponse showtimeInfoResponse = requireBookableShowtime(showtimeId, now);
        SeatPricingInfoResponse seatPricingInfoResponse = requireBookableSeat(showtimeInfoResponse, seatId);
        Long cinemaId = requireCinemaId(showtimeInfoResponse);
        return executeWithSeatLock(showtimeId, seatId, () -> transactionTemplate.execute(
                transactionStatus -> holdSeatInTransaction(
                        userId, showtimeInfoResponse, seatPricingInfoResponse, cinemaId, now)));
    }

    @Override
    public SeatHoldResponse releaseSeat(Long userId, Long showtimeId, Long seatId) {
        Instant now = Instant.now(clock);
        return executeWithSeatLock(showtimeId, seatId, () -> transactionTemplate.execute(
                transactionStatus -> releaseSeatInTransaction(userId, showtimeId, seatId, now)));
    }

    @Override
    public SeatHoldResponse getMyHold(Long userId, Long showtimeId) {
        Instant now = Instant.now(clock);
        return transactionTemplate.execute(transactionStatus -> bookingRepository
                .findFirstByUserIdAndStatus(userId, BookingStatus.PENDING)
                .filter(booking -> showtimeId.equals(booking.getShowtimeId()) && booking.getExpiresAt().isAfter(now))
                .map(booking -> toSeatHoldResponse(booking, now))
                .orElseGet(() -> emptyHold(showtimeId, now)));
    }

    @Override
    public void cancelPendingBooking(Long userId) {
        transactionTemplate.executeWithoutResult(transactionStatus -> bookingRepository
                .findFirstByUserIdAndStatus(userId, BookingStatus.PENDING)
                .ifPresent(booking -> closeBooking(booking, BookingStatus.CANCELLED)));
    }

    private SeatHoldResponse holdSeatInTransaction(
            Long userId,
            ShowtimeInfoResponse showtimeInfoResponse,
            SeatPricingInfoResponse seatPricingInfoResponse,
            Long cinemaId,
            Instant now) {
        Long showtimeId = showtimeInfoResponse.id();
        Long seatId = seatPricingInfoResponse.seatId();
        seatReservationRepository.deleteExpiredHold(showtimeId, seatId, SeatReservationStatus.HELD, now);
        SeatReservation existingSeatReservation =
                seatReservationRepository.findByShowtimeIdAndSeatId(showtimeId, seatId).orElse(null);
        Instant bookingCutoff = BookingWindow.lastBookableMoment(
                showtimeInfoResponse.startTime(), bookingPolicyProperties.lateBookingWindow());
        Booking booking = findOrCreateBooking(userId, showtimeId, cinemaId, now, bookingCutoff);
        if (existingSeatReservation != null) {
            if (existingSeatReservation.getUserId().equals(userId)
                    && booking.getId().equals(existingSeatReservation.getBookingId())) {
                return toSeatHoldResponse(booking, now);
            }
            throw seatAlreadyHeld();
        }
        List<SeatReservation> currentSeatReservations =
                seatReservationRepository.findByBookingIdOrderBySeatId(booking.getId());
        if (currentSeatReservations.size() >= BookingRules.MAX_SEATS_PER_BOOKING) {
            throw new AppException(
                    "Bạn chỉ có thể chọn tối đa " + BookingRules.MAX_SEATS_PER_BOOKING + " ghế cho mỗi lần đặt",
                    HttpStatus.BAD_REQUEST,
                    "SEAT_LIMIT_EXCEEDED");
        }
        SeatReservation seatReservation = new SeatReservation();
        seatReservation.setShowtimeId(showtimeId);
        seatReservation.setSeatId(seatId);
        seatReservation.setUserId(userId);
        seatReservation.setBookingId(booking.getId());
        seatReservation.setStatus(SeatReservationStatus.HELD);
        seatReservation.setReservedAt(now);
        seatReservation.setExpiresAt(booking.getExpiresAt());
        seatReservationRepository.saveAndFlush(seatReservation);
        SeatHoldResponse seatHoldResponse = toSeatHoldResponse(booking, now);
        updateBookingAmounts(booking, seatHoldResponse.totalAmount());
        broadcastAfterCommit(showtimeId, List.of(new SeatStateChange(seatId, SeatAvailability.HELD)));
        return seatHoldResponse;
    }

    private SeatHoldResponse releaseSeatInTransaction(Long userId, Long showtimeId, Long seatId, Instant now) {
        SeatReservation seatReservation =
                seatReservationRepository.findByShowtimeIdAndSeatId(showtimeId, seatId).orElse(null);
        if (seatReservation == null || seatReservation.getStatus() != SeatReservationStatus.HELD) {
            return getMyHoldInTransaction(userId, showtimeId, now);
        }
        if (!seatReservation.getUserId().equals(userId)) {
            throw new AppException("Ghế này không do bạn giữ", HttpStatus.FORBIDDEN, "SEAT_NOT_HELD_BY_YOU");
        }
        Booking booking = bookingRepository.findById(seatReservation.getBookingId()).orElseThrow();
        seatReservationRepository.delete(seatReservation);
        seatReservationRepository.flush();
        broadcastAfterCommit(showtimeId, List.of(new SeatStateChange(seatId, SeatAvailability.AVAILABLE)));
        if (seatReservationRepository.findByBookingIdOrderBySeatId(booking.getId()).isEmpty()) {
            booking.setStatus(BookingStatus.CANCELLED);
            updateBookingAmounts(booking, BigDecimal.ZERO);
            return emptyHold(showtimeId, now);
        }
        SeatHoldResponse seatHoldResponse = toSeatHoldResponse(booking, now);
        updateBookingAmounts(booking, seatHoldResponse.totalAmount());
        return seatHoldResponse;
    }

    private SeatHoldResponse getMyHoldInTransaction(Long userId, Long showtimeId, Instant now) {
        return bookingRepository.findFirstByUserIdAndStatus(userId, BookingStatus.PENDING)
                .filter(booking -> showtimeId.equals(booking.getShowtimeId()) && booking.getExpiresAt().isAfter(now))
                .map(booking -> toSeatHoldResponse(booking, now))
                .orElseGet(() -> emptyHold(showtimeId, now));
    }

    private Booking findOrCreateBooking(
            Long userId, Long showtimeId, Long cinemaId, Instant now, Instant bookingCutoff) {
        Booking pendingBooking =
                bookingRepository.findFirstByUserIdAndStatus(userId, BookingStatus.PENDING).orElse(null);
        if (pendingBooking != null && !pendingBooking.getExpiresAt().isAfter(now)) {
            closeBooking(pendingBooking, BookingStatus.EXPIRED);
            pendingBooking = null;
        }
        if (pendingBooking != null) {
            if (!showtimeId.equals(pendingBooking.getShowtimeId())) {
                throw new AppException(
                        "Bạn đang giữ ghế ở một suất chiếu khác. Hãy hủy lượt giữ ghế đó trước khi chọn suất mới",
                        HttpStatus.CONFLICT,
                        "PENDING_BOOKING_OTHER_SHOWTIME");
            }
            return pendingBooking;
        }
        Booking booking = new Booking();
        booking.setBookingCode("BK" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        booking.setUserId(userId);
        booking.setCinemaId(cinemaId);
        booking.setShowtimeId(showtimeId);
        booking.setStatus(BookingStatus.PENDING);
        Instant holdExpiry = now.plus(BookingRules.SEAT_HOLD_DURATION);
        booking.setExpiresAt(holdExpiry.isAfter(bookingCutoff) ? bookingCutoff : holdExpiry);
        return bookingRepository.saveAndFlush(booking);
    }

    private void closeBooking(Booking booking, BookingStatus closedStatus) {
        List<SeatReservation> seatReservations = seatReservationRepository.findByBookingIdOrderBySeatId(booking.getId())
                .stream()
                .filter(seatReservation -> seatReservation.getStatus() == SeatReservationStatus.HELD)
                .toList();
        seatReservationRepository.deleteAllInBatch(seatReservations);
        booking.setStatus(closedStatus);
        bookingRepository.saveAndFlush(booking);
        if (!seatReservations.isEmpty()) {
            broadcastAfterCommit(
                    booking.getShowtimeId(),
                    seatReservations.stream()
                            .map(seatReservation -> new SeatStateChange(seatReservation.getSeatId(), SeatAvailability.AVAILABLE))
                            .toList());
        }
    }

    private void updateBookingAmounts(Booking booking, BigDecimal totalAmount) {
        booking.setTotalAmount(totalAmount);
        booking.setFinalAmount(totalAmount.subtract(booking.getDiscountAmount()));
        bookingRepository.save(booking);
    }

    private SeatHoldResponse toSeatHoldResponse(Booking booking, Instant now) {
        List<Long> seatIds = seatReservationRepository.findByBookingIdOrderBySeatId(booking.getId()).stream()
                .map(SeatReservation::getSeatId)
                .toList();
        Map<Long, SeatPricingInfoResponse> seatPricingInfoResponses = cinemaQueryService.findSeatPricingInfos(seatIds);
        Map<String, BigDecimal> seatPricesByType = dynamicPricingService.getShowtimePrices(booking.getShowtimeId())
                .seatTypePrices().stream()
                .collect(Collectors.toMap(SeatTypePriceResponse::seatTypeCode, SeatTypePriceResponse::totalPrice));
        List<HeldSeatResponse> heldSeatResponses = seatIds.stream()
                .map(seatId -> new HeldSeatResponse(
                        seatId, seatPricesByType.get(seatPricingInfoResponses.get(seatId).seatTypeCode())))
                .toList();
        BigDecimal totalAmount = heldSeatResponses.stream()
                .map(HeldSeatResponse::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SeatHoldResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getShowtimeId(),
                booking.getExpiresAt(),
                now,
                heldSeatResponses,
                totalAmount);
    }

    private SeatHoldResponse emptyHold(Long showtimeId, Instant now) {
        return new SeatHoldResponse(null, null, showtimeId, null, now, List.of(), BigDecimal.ZERO);
    }

    private ShowtimeInfoResponse requireBookableShowtime(Long showtimeId, Instant now) {
        ShowtimeInfoResponse showtimeInfoResponse = showtimeQueryService.findShowtimeInfo(showtimeId)
                .filter(foundShowtimeInfo -> foundShowtimeInfo.status() == ShowtimeStatus.ACTIVE)
                .orElseThrow(() -> new AppException("Không tìm thấy suất chiếu", HttpStatus.NOT_FOUND, "SHOWTIME_NOT_FOUND"));
        if (!BookingWindow.isOpen(showtimeInfoResponse.startTime(), now, bookingPolicyProperties.lateBookingWindow())) {
            throw new AppException("Đã hết thời gian đặt vé cho suất chiếu này", HttpStatus.CONFLICT, "SHOWTIME_STARTED");
        }
        return showtimeInfoResponse;
    }

    private SeatPricingInfoResponse requireBookableSeat(ShowtimeInfoResponse showtimeInfoResponse, Long seatId) {
        SeatPricingInfoResponse seatPricingInfoResponse =
                cinemaQueryService.findSeatPricingInfos(List.of(seatId)).get(seatId);
        if (seatPricingInfoResponse == null
                || !seatPricingInfoResponse.hallId().equals(showtimeInfoResponse.hallId())) {
            throw new AppException(
                    "Ghế không thuộc phòng chiếu của suất chiếu này", HttpStatus.BAD_REQUEST, "SEAT_NOT_IN_SHOWTIME_HALL");
        }
        if (!seatPricingInfoResponse.bookable()) {
            throw new AppException("Ghế đang bảo trì hoặc đã bị gỡ", HttpStatus.CONFLICT, "SEAT_NOT_BOOKABLE");
        }
        return seatPricingInfoResponse;
    }

    private Long requireCinemaId(ShowtimeInfoResponse showtimeInfoResponse) {
        HallInfoResponse hallInfoResponse = cinemaQueryService.findHallInfos(List.of(showtimeInfoResponse.hallId()))
                .get(showtimeInfoResponse.hallId());
        if (hallInfoResponse == null || hallInfoResponse.deleted()) {
            throw new AppException("Không tìm thấy phòng chiếu", HttpStatus.NOT_FOUND, "HALL_NOT_FOUND");
        }
        return hallInfoResponse.cinemaId();
    }

    private SeatHoldResponse executeWithSeatLock(Long showtimeId, Long seatId, Supplier<SeatHoldResponse> action) {
        RLock lock = redissonClient.getLock(SEAT_LOCK_KEY_TEMPLATE.formatted(showtimeId, seatId));
        boolean locked;
        try {
            locked = lock.tryLock(LOCK_WAIT_MILLISECONDS, LOCK_LEASE_MILLISECONDS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw seatLockUnavailable();
        } catch (RedisException redisException) {
            throw seatLockUnavailable();
        }
        if (!locked) {
            throw seatAlreadyHeld();
        }
        try {
            return action.get();
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            String message = String.valueOf(dataIntegrityViolationException.getMostSpecificCause().getMessage());
            if (message.contains(SEAT_UNIQUE_CONSTRAINT)) {
                throw seatAlreadyHeld();
            }
            throw new AppException(
                    "Thao tác bị xung đột, vui lòng thử lại", HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void broadcastAfterCommit(Long showtimeId, List<SeatStateChange> seatStateChanges) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                seatStatusBroadcaster.broadcast(showtimeId, seatStateChanges);
            }
        });
    }

    private AppException seatAlreadyHeld() {
        return new AppException("Ghế vừa có người chọn", HttpStatus.CONFLICT, "SEAT_ALREADY_HELD");
    }

    private AppException seatLockUnavailable() {
        return new AppException(
                "Hệ thống đang bận, vui lòng thử lại sau ít giây", HttpStatus.SERVICE_UNAVAILABLE, "SEAT_LOCK_UNAVAILABLE");
    }
}
