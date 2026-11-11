package com.cinema.modules.booking.service;

import com.cinema.common.exception.AppException;
import com.cinema.config.properties.BookingPolicyProperties;
import com.cinema.modules.booking.dto.message.SeatStateChange;
import com.cinema.modules.booking.dto.response.SeatHoldResponse;
import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.entity.SeatReservation;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.enums.SeatAvailability;
import com.cinema.modules.booking.enums.SeatReservationStatus;
import com.cinema.modules.booking.repository.BookingFnbDetailRepository;
import com.cinema.modules.booking.repository.BookingRepository;
import com.cinema.modules.booking.repository.SeatReservationRepository;
import com.cinema.modules.booking.service.interfaces.ISeatStatusBroadcaster;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.dto.response.SeatPricingInfoResponse;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.showtime.dto.response.SeatTypePriceResponse;
import com.cinema.modules.showtime.dto.response.ShowtimeInfoResponse;
import com.cinema.modules.showtime.dto.response.ShowtimePriceResponse;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import com.cinema.modules.showtime.service.interfaces.IDynamicPricingService;
import com.cinema.modules.showtime.service.interfaces.IShowtimeQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatHoldServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long SHOWTIME_ID = 5L;
    private static final Long HALL_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SeatReservationRepository seatReservationRepository;

    @Mock
    private BookingFnbDetailRepository bookingFnbDetailRepository;

    @Mock
    private IShowtimeQueryService showtimeQueryService;

    @Mock
    private IDynamicPricingService dynamicPricingService;

    @Mock
    private ICinemaQueryService cinemaQueryService;

    @Mock
    private ISeatStatusBroadcaster seatStatusBroadcaster;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Mock
    private PlatformTransactionManager platformTransactionManager;

    private final List<SeatReservation> storedSeatReservations = new ArrayList<>();
    private SeatHoldService seatHoldService;
    private Booking storedBooking;

    @BeforeEach
    void setUp() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        seatHoldService = new SeatHoldService(
                bookingRepository,
                seatReservationRepository,
                bookingFnbDetailRepository,
                showtimeQueryService,
                dynamicPricingService,
                cinemaQueryService,
                seatStatusBroadcaster,
                redissonClient,
                new TransactionTemplate(platformTransactionManager),
                new BookingPolicyProperties(30),
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(platformTransactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(redissonClient.getLock(any(String.class))).thenReturn(rLock);
        lenient().when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);
        lenient().when(showtimeQueryService.findShowtimeInfo(SHOWTIME_ID)).thenReturn(Optional.of(
                new ShowtimeInfoResponse(SHOWTIME_ID, 7L, HALL_ID, NOW.plusSeconds(3600), NOW.plusSeconds(10800),
                        new BigDecimal("90000"), ShowtimeStatus.ACTIVE)));
        lenient().when(cinemaQueryService.findHallInfos(List.of(HALL_ID))).thenReturn(Map.of(
                HALL_ID, new HallInfoResponse(HALL_ID, 1L, "Phòng 1", ScreenType.SCREEN_2D, HallStatus.ACTIVE, false)));
        lenient().when(cinemaQueryService.findSeatPricingInfos(any())).thenAnswer(invocationOnMock -> {
            java.util.Collection<Long> seatIds = invocationOnMock.getArgument(0);
            java.util.Map<Long, SeatPricingInfoResponse> seatPricingInfoResponses = new java.util.HashMap<>();
            for (Long seatId : seatIds) {
                Long hallId = seatId >= 900 ? 99L : HALL_ID;
                seatPricingInfoResponses.put(seatId, new SeatPricingInfoResponse(
                        seatId, hallId, "STANDARD", "Thường", BigDecimal.ZERO, seatId != 800));
            }
            return seatPricingInfoResponses;
        });
        lenient().when(dynamicPricingService.getShowtimePrices(SHOWTIME_ID)).thenReturn(new ShowtimePriceResponse(
                SHOWTIME_ID, ScreenType.SCREEN_2D, NOW.plusSeconds(3600), new BigDecimal("90000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(),
                List.of(new SeatTypePriceResponse("STANDARD", "Thường", BigDecimal.ZERO, new BigDecimal("100000")))));
        lenient().when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(invocationOnMock -> {
            Booking booking = invocationOnMock.getArgument(0);
            booking.setId(100L);
            storedBooking = booking;
            return booking;
        });
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        lenient().when(seatReservationRepository.saveAndFlush(any(SeatReservation.class)))
                .thenAnswer(invocationOnMock -> {
                    SeatReservation seatReservation = invocationOnMock.getArgument(0);
                    storedSeatReservations.add(seatReservation);
                    return seatReservation;
                });
        lenient().when(seatReservationRepository.findByBookingIdOrderBySeatId(anyLong()))
                .thenAnswer(invocationOnMock -> new ArrayList<>(storedSeatReservations));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void firstHoldCreatesPendingBookingWithTenMinuteExpiryAndBroadcastsAfterCommit() {
        when(bookingRepository.findFirstByUserIdAndStatus(USER_ID, BookingStatus.PENDING)).thenReturn(Optional.empty());
        when(seatReservationRepository.findByShowtimeIdAndSeatId(SHOWTIME_ID, 11L)).thenReturn(Optional.empty());

        SeatHoldResponse seatHoldResponse = seatHoldService.holdSeat(USER_ID, SHOWTIME_ID, 11L);

        assertEquals(NOW.plusSeconds(600), storedBooking.getExpiresAt());
        assertEquals(BookingStatus.PENDING, storedBooking.getStatus());
        assertEquals(SHOWTIME_ID, storedBooking.getShowtimeId());
        assertEquals(1L, storedBooking.getCinemaId());
        assertEquals(SeatReservationStatus.HELD, storedSeatReservations.get(0).getStatus());
        assertEquals(storedBooking.getExpiresAt(), storedSeatReservations.get(0).getExpiresAt());
        assertEquals(new BigDecimal("100000"), seatHoldResponse.totalAmount());
        assertEquals(new BigDecimal("100000"), storedBooking.getFinalAmount());
        verify(seatStatusBroadcaster, never()).broadcast(any(), any());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(seatStatusBroadcaster).broadcast(SHOWTIME_ID, List.of(new SeatStateChange(11L, SeatAvailability.HELD)));
        verify(rLock).unlock();
    }

    @Test
    void holdIsStillAllowedTenMinutesAfterTheShowtimeStarted() {
        stubShowtimeStartingAt(NOW.minusSeconds(600));
        when(bookingRepository.findFirstByUserIdAndStatus(USER_ID, BookingStatus.PENDING)).thenReturn(Optional.empty());
        when(seatReservationRepository.findByShowtimeIdAndSeatId(SHOWTIME_ID, 11L)).thenReturn(Optional.empty());

        SeatHoldResponse seatHoldResponse = seatHoldService.holdSeat(USER_ID, SHOWTIME_ID, 11L);

        assertEquals(1, seatHoldResponse.seats().size());
    }

    @Test
    void holdIsRejectedMoreThanThirtyMinutesAfterTheShowtimeStarted() {
        stubShowtimeStartingAt(NOW.minusSeconds(31 * 60));

        AppException appException =
                assertThrows(AppException.class, () -> seatHoldService.holdSeat(USER_ID, SHOWTIME_ID, 11L));

        assertEquals("SHOWTIME_STARTED", appException.getErrorCode());
        verify(redissonClient, never()).getLock(any(String.class));
    }

    @Test
    void holdExpiryIsCappedAtTheEndOfTheLateBookingWindow() {
        stubShowtimeStartingAt(NOW.minusSeconds(25 * 60));
        when(bookingRepository.findFirstByUserIdAndStatus(USER_ID, BookingStatus.PENDING)).thenReturn(Optional.empty());
        when(seatReservationRepository.findByShowtimeIdAndSeatId(SHOWTIME_ID, 11L)).thenReturn(Optional.empty());

        seatHoldService.holdSeat(USER_ID, SHOWTIME_ID, 11L);

        assertEquals(NOW.plusSeconds(5 * 60), storedBooking.getExpiresAt());
        assertEquals(NOW.plusSeconds(5 * 60), storedSeatReservations.get(0).getExpiresAt());
    }

    @Test
    void lockNotAcquiredReturnsConflictWithoutTouchingDatabase() throws Exception {
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        AppException appException =
                assertThrows(AppException.class, () -> seatHoldService.holdSeat(USER_ID, SHOWTIME_ID, 11L));

        assertEquals("SEAT_ALREADY_HELD", appException.getErrorCode());
        verify(seatReservationRepository, never()).saveAndFlush(any());
    }

    @Test
    void redisFailureReturnsServiceUnavailableAndDoesNotHoldTheSeat() throws Exception {
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenThrow(new RedisException("down"));

        AppException appException =
                assertThrows(AppException.class, () -> seatHoldService.holdSeat(USER_ID, SHOWTIME_ID, 11L));

        assertEquals("SEAT_LOCK_UNAVAILABLE", appException.getErrorCode());
        verify(seatReservationRepository, never()).saveAndFlush(any());
    }

    @Test
    void seatHeldByAnotherUserIsRejected() {
        SeatReservation otherUserReservation = reservation(11L, OTHER_USER_ID, 200L);
        when(bookingRepository.findFirstByUserIdAndStatus(USER_ID, BookingStatus.PENDING)).thenReturn(Optional.empty());
        when(seatReservationRepository.findByShowtimeIdAndSeatId(SHOWTIME_ID, 11L))
                .thenReturn(Optional.of(otherUserReservation));

        AppException appException =
                assertThrows(AppException.class, () -> seatHoldService.holdSeat(USER_ID, SHOWTIME_ID, 11L));

        assertEquals("SEAT_ALREADY_HELD", appException.getErrorCode());
        assertTrue(storedSeatReservations.isEmpty());
    }

    @Test
    void uniqueConstraintViolationFromRaceIsReportedAsSeatAlreadyHeld() {
        when(bookingRepository.findFirstByUserIdAndStatus(USER_ID, BookingStatus.PENDING)).thenReturn(Optional.empty());
        when(seatReservationRepository.findByShowtimeIdAndSeatId(SHOWTIME_ID, 11L)).thenReturn(Optional.empty());
        when(seatReservationRepository.saveAndFlush(any(SeatReservation.class))).thenThrow(
                new DataIntegrityViolationException("x", new RuntimeException("violates unique constraint \"uq_showtime_seat\"")));

        AppException appException =
                assertThrows(AppException.class, () -> seatHoldService.holdSeat(USER_ID, SHOWTIME_ID, 11L));

        assertEquals("SEAT_ALREADY_HELD", appException.getErrorCode());
        verify(rLock).unlock();
    }

    @Test
    void pendingBookingOfAnotherShowtimeBlocksNewHold() {
        Booking otherShowtimeBooking = booking(300L, 99L, NOW.plusSeconds(300));
        when(bookingRepository.findFirstByUserIdAndStatus(USER_ID, BookingStatus.PENDING))
                .thenReturn(Optional.of(otherShowtimeBooking));

        AppException appException =
                assertThrows(AppException.class, () -> seatHoldService.holdSeat(USER_ID, SHOWTIME_ID, 11L));

        assertEquals("PENDING_BOOKING_OTHER_SHOWTIME", appException.getErrorCode());
    }

    @Test
    void ninthSeatIsRejected() {
        Booking pendingBooking = booking(100L, SHOWTIME_ID, NOW.plusSeconds(300));
        for (long seatId = 1; seatId <= 8; seatId++) {
            storedSeatReservations.add(reservation(seatId, USER_ID, 100L));
        }
        when(bookingRepository.findFirstByUserIdAndStatus(USER_ID, BookingStatus.PENDING))
                .thenReturn(Optional.of(pendingBooking));
        when(seatReservationRepository.findByShowtimeIdAndSeatId(SHOWTIME_ID, 11L)).thenReturn(Optional.empty());

        AppException appException =
                assertThrows(AppException.class, () -> seatHoldService.holdSeat(USER_ID, SHOWTIME_ID, 11L));

        assertEquals("SEAT_LIMIT_EXCEEDED", appException.getErrorCode());
    }

    @Test
    void seatOfAnotherHallIsRejectedBeforeAnyLock() {
        AppException appException =
                assertThrows(AppException.class, () -> seatHoldService.holdSeat(USER_ID, SHOWTIME_ID, 900L));

        assertEquals("SEAT_NOT_IN_SHOWTIME_HALL", appException.getErrorCode());
        verify(redissonClient, never()).getLock(any(String.class));
    }

    @Test
    void maintenanceSeatIsRejected() {
        AppException appException =
                assertThrows(AppException.class, () -> seatHoldService.holdSeat(USER_ID, SHOWTIME_ID, 800L));

        assertEquals("SEAT_NOT_BOOKABLE", appException.getErrorCode());
    }

    @Test
    void releasingAnotherUsersSeatIsForbidden() {
        when(seatReservationRepository.findByShowtimeIdAndSeatId(SHOWTIME_ID, 11L))
                .thenReturn(Optional.of(reservation(11L, OTHER_USER_ID, 200L)));

        AppException appException =
                assertThrows(AppException.class, () -> seatHoldService.releaseSeat(USER_ID, SHOWTIME_ID, 11L));

        assertEquals("SEAT_NOT_HELD_BY_YOU", appException.getErrorCode());
    }

    @Test
    void releasingTheLastSeatCancelsTheBookingAndBroadcastsAvailable() {
        SeatReservation ownReservation = reservation(11L, USER_ID, 100L);
        Booking pendingBooking = booking(100L, SHOWTIME_ID, NOW.plusSeconds(300));
        when(seatReservationRepository.findByShowtimeIdAndSeatId(SHOWTIME_ID, 11L))
                .thenReturn(Optional.of(ownReservation));
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(pendingBooking));

        SeatHoldResponse seatHoldResponse = seatHoldService.releaseSeat(USER_ID, SHOWTIME_ID, 11L);

        verify(seatReservationRepository).delete(ownReservation);
        assertEquals(BookingStatus.CANCELLED, pendingBooking.getStatus());
        assertTrue(seatHoldResponse.seats().isEmpty());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(seatStatusBroadcaster)
                .broadcast(eq(SHOWTIME_ID), eq(List.of(new SeatStateChange(11L, SeatAvailability.AVAILABLE))));
    }

    private void stubShowtimeStartingAt(Instant startTime) {
        when(showtimeQueryService.findShowtimeInfo(SHOWTIME_ID)).thenReturn(Optional.of(
                new ShowtimeInfoResponse(SHOWTIME_ID, 7L, HALL_ID, startTime, startTime.plusSeconds(7200),
                        new BigDecimal("90000"), ShowtimeStatus.ACTIVE)));
    }

    private Booking booking(Long id, Long showtimeId, Instant expiresAt) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setUserId(USER_ID);
        booking.setCinemaId(1L);
        booking.setShowtimeId(showtimeId);
        booking.setExpiresAt(expiresAt);
        booking.setStatus(BookingStatus.PENDING);
        return booking;
    }

    private SeatReservation reservation(Long seatId, Long userId, Long bookingId) {
        SeatReservation seatReservation = new SeatReservation();
        seatReservation.setShowtimeId(SHOWTIME_ID);
        seatReservation.setSeatId(seatId);
        seatReservation.setUserId(userId);
        seatReservation.setBookingId(bookingId);
        seatReservation.setStatus(SeatReservationStatus.HELD);
        seatReservation.setReservedAt(NOW);
        seatReservation.setExpiresAt(NOW.plusSeconds(300));
        return seatReservation;
    }
}
