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
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.fnb.dto.response.FnbProductInfoResponse;
import com.cinema.modules.fnb.service.interfaces.IFnbQueryService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FnbPickupServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T13:00:00Z");
    private static final Instant SHOWTIME_START = Instant.parse("2026-11-12T13:30:00Z");
    private static final Instant SHOWTIME_END = Instant.parse("2026-11-12T15:30:00Z");
    private static final Long STAFF_USER_ID = 7L;
    private static final Long CINEMA_ID = 1L;
    private static final Long BOOKING_ID = 100L;
    private static final Long COMBO_DETAIL_ID = 501L;
    private static final Long ITEM_DETAIL_ID = 502L;
    private static final String QR_CODE = "QRABCDEFGHJKLMNPQRSTUVWX";

    @Mock
    private ScanGuard scanGuard;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingFnbDetailRepository bookingFnbDetailRepository;

    @Mock
    private FnbDeliveryLogRepository fnbDeliveryLogRepository;

    @Mock
    private IBookingDetailService bookingDetailService;

    @Mock
    private IBranchAccessService branchAccessService;

    @Mock
    private IUserQueryService userQueryService;

    @Mock
    private IFnbQueryService fnbQueryService;

    @Mock
    private IShowtimeQueryService showtimeQueryService;

    @Mock
    private IMovieQueryService movieQueryService;

    @Mock
    private ICinemaQueryService cinemaQueryService;

    private FnbPickupService fnbPickupService;
    private Booking booking;
    private BookingFnbDetail comboDetail;
    private BookingFnbDetail itemDetail;
    private Instant clockInstant = NOW;

    @BeforeEach
    void setUp() {
        Clock mutableClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return clockInstant;
            }
        };
        fnbPickupService = new FnbPickupService(
                bookingRepository,
                bookingFnbDetailRepository,
                fnbDeliveryLogRepository,
                bookingDetailService,
                branchAccessService,
                userQueryService,
                fnbQueryService,
                showtimeQueryService,
                movieQueryService,
                cinemaQueryService,
                scanGuard,
                mutableClock);
        booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setBookingCode("BK62072B12C606");
        booking.setCinemaId(CINEMA_ID);
        booking.setShowtimeId(20L);
        booking.setStatus(BookingStatus.PAID);
        booking.setQrCode(QR_CODE);
        comboDetail = buildDetail(COMBO_DETAIL_ID, 30L, null, 2);
        itemDetail = buildDetail(ITEM_DETAIL_ID, null, 7L, 3);

        lenient().when(bookingRepository.findByQrCode(QR_CODE)).thenReturn(Optional.of(booking));
        lenient().when(bookingRepository.findByBookingCode("BK62072B12C606")).thenReturn(Optional.of(booking));
        lenient().when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        lenient().when(bookingFnbDetailRepository.findByBookingIdOrderByIdAsc(BOOKING_ID))
                .thenAnswer(invocation -> List.of(comboDetail, itemDetail));
        lenient().when(branchAccessService.canAccessCinema(anyLong(), anyLong())).thenReturn(true);
        lenient().when(fnbQueryService.findComboInfos(anyCollection()))
                .thenReturn(Map.of(30L, new FnbProductInfoResponse(30L, "Combo đôi", null, BigDecimal.TEN, true)));
        lenient().when(fnbQueryService.findItemInfos(anyCollection()))
                .thenReturn(Map.of(7L, new FnbProductInfoResponse(7L, "Bắp ngọt", null, BigDecimal.TEN, true)));
        lenient().when(fnbQueryService.findComboCompositions(any())).thenReturn(Map.of(30L, "1 bắp + 2 nước"));
        lenient().when(userQueryService.findContact(STAFF_USER_ID))
                .thenReturn(Optional.of(new UserContactResponse(STAFF_USER_ID, "staff@cinema.vn", "Nguyễn Văn A")));
        lenient().when(bookingDetailService.findBookingDetail(BOOKING_ID))
                .thenAnswer(invocation -> Optional.of(buildBookingDetail()));
    }

    @Test
    void scanReturnsValidWithLinesAndRemainingQuantities() {
        FnbPickupScanResponse fnbPickupScanResponse = scan(QR_CODE);

        assertEquals(FnbPickupResultCode.VALID, fnbPickupScanResponse.resultCode());
        assertTrue(fnbPickupScanResponse.canDeliver());
        assertEquals(2, fnbPickupScanResponse.booking().lines().size());
        assertEquals("1 bắp + 2 nước", fnbPickupScanResponse.booking().lines().get(0).composition());
        assertEquals(5, fnbPickupScanResponse.booking().totalQuantity());
        assertEquals(5, fnbPickupScanResponse.booking().remainingQuantity());
    }

    @Test
    void scanAcceptsBookingCodeTypedByHand() {
        assertEquals(FnbPickupResultCode.VALID, scan(" bk62072b12c606 ").resultCode());
    }

    @Test
    void scanReturnsNotFoundForUnknownCode() {
        FnbPickupScanResponse fnbPickupScanResponse = scan("QRUNKNOWN");

        assertEquals(FnbPickupResultCode.NOT_FOUND, fnbPickupScanResponse.resultCode());
        assertFalse(fnbPickupScanResponse.canDeliver());
        assertNull(fnbPickupScanResponse.booking());
    }

    @Test
    void scanReturnsNotYetTimeMoreThanOneHourBeforeShowtime() {
        clockInstant = SHOWTIME_START.minusSeconds(61 * 60);

        FnbPickupScanResponse fnbPickupScanResponse = scan(QR_CODE);

        assertEquals(FnbPickupResultCode.NOT_YET_TIME, fnbPickupScanResponse.resultCode());
        assertFalse(fnbPickupScanResponse.canDeliver());
    }

    @Test
    void scanReturnsValidExactlyOneHourBeforeShowtime() {
        clockInstant = SHOWTIME_START.minusSeconds(60 * 60);

        assertEquals(FnbPickupResultCode.VALID, scan(QR_CODE).resultCode());
    }

    @Test
    void scanReturnsExpiredAfterShowtimeEnds() {
        clockInstant = SHOWTIME_END.plusSeconds(1);

        assertEquals(FnbPickupResultCode.EXPIRED, scan(QR_CODE).resultCode());
    }

    @Test
    void scanReturnsAllDeliveredWhenNothingRemains() {
        comboDetail.setDeliveredQuantity(2);
        itemDetail.setDeliveredQuantity(3);

        FnbPickupScanResponse fnbPickupScanResponse = scan(QR_CODE);

        assertEquals(FnbPickupResultCode.ALL_DELIVERED, fnbPickupScanResponse.resultCode());
        assertFalse(fnbPickupScanResponse.canDeliver());
    }

    @Test
    void scanReturnsNoFnbWhenBookingHasNoFnbLines() {
        when(bookingFnbDetailRepository.findByBookingIdOrderByIdAsc(BOOKING_ID)).thenReturn(List.of());

        assertEquals(FnbPickupResultCode.NO_FNB, scan(QR_CODE).resultCode());
    }

    @Test
    void scanReturnsWrongBranchForAnotherCinema() {
        FnbPickupScanResponse fnbPickupScanResponse =
                fnbPickupService.scan(STAFF_USER_ID, new FnbPickupScanRequest(QR_CODE, 2L));

        assertEquals(FnbPickupResultCode.WRONG_BRANCH, fnbPickupScanResponse.resultCode());
        assertFalse(fnbPickupScanResponse.canDeliver());
    }

    @Test
    void scanReturnsNotPaidForPendingBooking() {
        booking.setStatus(BookingStatus.PENDING);

        assertEquals(FnbPickupResultCode.NOT_PAID, scan(QR_CODE).resultCode());
    }

    @Test
    void deliverWithoutLinesDeliversEverythingRemaining() {
        FnbPickupScanResponse fnbPickupScanResponse = deliver(null);

        assertEquals(FnbPickupResultCode.DELIVERED, fnbPickupScanResponse.resultCode());
        assertEquals(2, comboDetail.getDeliveredQuantity());
        assertEquals(3, itemDetail.getDeliveredQuantity());
        assertEquals(STAFF_USER_ID, comboDetail.getDeliveredByUserId());
        assertEquals(NOW, comboDetail.getDeliveredAt());
        assertEquals(0, fnbPickupScanResponse.booking().remainingQuantity());
        assertFalse(fnbPickupScanResponse.canDeliver());
        assertEquals("Nguyễn Văn A", fnbPickupScanResponse.booking().lines().get(0).deliveredByStaffName());
        verify(bookingFnbDetailRepository).saveAll(any());
    }

    @Test
    void deliverPartiallyOnlyChangesTheChosenLine() {
        FnbPickupScanResponse fnbPickupScanResponse =
                deliver(List.of(new FnbPickupDeliverLineRequest(ITEM_DETAIL_ID, 1)));

        assertEquals(0, comboDetail.getDeliveredQuantity());
        assertNull(comboDetail.getDeliveredAt());
        assertEquals(1, itemDetail.getDeliveredQuantity());
        assertEquals(4, fnbPickupScanResponse.booking().remainingQuantity());
        assertTrue(fnbPickupScanResponse.canDeliver());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deliverWritesOneHistoryEntryPerDeliveredLine() {
        deliver(List.of(new FnbPickupDeliverLineRequest(ITEM_DETAIL_ID, 2)));

        ArgumentCaptor<List<FnbDeliveryLog>> fnbDeliveryLogCaptor = ArgumentCaptor.forClass(List.class);
        verify(fnbDeliveryLogRepository).saveAll(fnbDeliveryLogCaptor.capture());
        assertEquals(1, fnbDeliveryLogCaptor.getValue().size());
        FnbDeliveryLog fnbDeliveryLog = fnbDeliveryLogCaptor.getValue().get(0);
        assertEquals("Bắp ngọt", fnbDeliveryLog.getProductName());
        assertEquals(2, fnbDeliveryLog.getQuantity());
        assertEquals(STAFF_USER_ID, fnbDeliveryLog.getStaffUserId());
        assertEquals(CINEMA_ID, fnbDeliveryLog.getCinemaId());
        assertEquals("BK62072B12C606", fnbDeliveryLog.getBookingCode());
        assertEquals(NOW, fnbDeliveryLog.getCreatedAt());
    }

    @Test
    void historyListsRecentDeliveriesWithStaffNames() {
        FnbDeliveryLog fnbDeliveryLog = new FnbDeliveryLog();
        fnbDeliveryLog.setId(1L);
        fnbDeliveryLog.setBookingCode("BK62072B12C606");
        fnbDeliveryLog.setProductName("Combo đôi");
        fnbDeliveryLog.setQuantity(1);
        fnbDeliveryLog.setStaffUserId(STAFF_USER_ID);
        fnbDeliveryLog.setCreatedAt(NOW);
        when(fnbDeliveryLogRepository.findByCinemaIdOrderByIdDesc(CINEMA_ID, PageRequest.of(0, 50)))
                .thenReturn(List.of(fnbDeliveryLog));

        List<FnbDeliveryLogResponse> fnbDeliveryLogResponses = fnbPickupService.findHistory(STAFF_USER_ID, CINEMA_ID);

        assertEquals(1, fnbDeliveryLogResponses.size());
        assertEquals("Combo đôi", fnbDeliveryLogResponses.get(0).productName());
        assertEquals("Nguyễn Văn A", fnbDeliveryLogResponses.get(0).staffName());
    }

    @Test
    void deliverCanBeRepeatedUntilLineIsComplete() {
        deliver(List.of(new FnbPickupDeliverLineRequest(ITEM_DETAIL_ID, 2)));
        deliver(List.of(new FnbPickupDeliverLineRequest(ITEM_DETAIL_ID, 1)));

        assertEquals(3, itemDetail.getDeliveredQuantity());
        AppException appException = assertThrows(AppException.class,
                () -> deliver(List.of(new FnbPickupDeliverLineRequest(ITEM_DETAIL_ID, 1))));
        assertEquals("FNB_QUANTITY_EXCEEDED", appException.getErrorCode());
        assertEquals(3, itemDetail.getDeliveredQuantity());
    }

    @Test
    void deliverRejectsQuantityGreaterThanRemainingWithoutChangingAnything() {
        itemDetail.setDeliveredQuantity(2);

        AppException appException = assertThrows(AppException.class,
                () -> deliver(List.of(new FnbPickupDeliverLineRequest(ITEM_DETAIL_ID, 2))));

        assertEquals(HttpStatus.CONFLICT, appException.getStatus());
        assertEquals("FNB_QUANTITY_EXCEEDED", appException.getErrorCode());
        assertEquals(2, itemDetail.getDeliveredQuantity());
        verify(bookingFnbDetailRepository, never()).saveAll(any());
    }

    @Test
    void deliverRejectsLineThatDoesNotBelongToTheBooking() {
        AppException appException = assertThrows(AppException.class,
                () -> deliver(List.of(new FnbPickupDeliverLineRequest(999L, 1))));

        assertEquals(HttpStatus.BAD_REQUEST, appException.getStatus());
        assertEquals("INVALID_FNB_LINE", appException.getErrorCode());
    }

    @Test
    void deliverRejectsDuplicatedLines() {
        AppException appException = assertThrows(AppException.class, () -> deliver(List.of(
                new FnbPickupDeliverLineRequest(ITEM_DETAIL_ID, 1),
                new FnbPickupDeliverLineRequest(ITEM_DETAIL_ID, 1))));

        assertEquals("DUPLICATE_FNB_LINE", appException.getErrorCode());
    }

    @Test
    void deliverIsBlockedOutsideThePickupWindow() {
        clockInstant = SHOWTIME_END.plusSeconds(1);

        FnbPickupScanResponse fnbPickupScanResponse = deliver(null);

        assertEquals(FnbPickupResultCode.EXPIRED, fnbPickupScanResponse.resultCode());
        assertEquals(0, comboDetail.getDeliveredQuantity());
        verify(bookingFnbDetailRepository, never()).saveAll(any());
        verify(fnbDeliveryLogRepository, never()).saveAll(any());
    }

    @Test
    void deliverIsBlockedWhenEverythingWasAlreadyDelivered() {
        comboDetail.setDeliveredQuantity(2);
        itemDetail.setDeliveredQuantity(3);

        FnbPickupScanResponse fnbPickupScanResponse = deliver(null);

        assertEquals(FnbPickupResultCode.ALL_DELIVERED, fnbPickupScanResponse.resultCode());
        verify(bookingFnbDetailRepository, never()).saveAll(any());
    }

    @Test
    void deliverThrowsNotFoundForUnknownCode() {
        AppException appException = assertThrows(AppException.class, () -> fnbPickupService.deliver(
                STAFF_USER_ID, new FnbPickupDeliverRequest("QRUNKNOWN", CINEMA_ID, null)));

        assertEquals(HttpStatus.NOT_FOUND, appException.getStatus());
    }

    @Test
    void queueGroupsPendingBookingsByShowtimeWithRemainingQuantities() {
        itemDetail.setDeliveredQuantity(1);
        ShowtimeInfoResponse showtimeInfoResponse = new ShowtimeInfoResponse(
                20L, 3L, 5L, SHOWTIME_START, SHOWTIME_END, BigDecimal.TEN, ShowtimeStatus.ACTIVE);
        when(showtimeQueryService.findActiveShowtimeInfosBetween(any(), any())).thenReturn(List.of(showtimeInfoResponse));
        when(bookingRepository.findWithUndeliveredFnb(CINEMA_ID, BookingStatus.PAID, List.of(20L)))
                .thenReturn(List.of(booking));
        when(bookingFnbDetailRepository.findByBookingIdInOrderByIdAsc(List.of(BOOKING_ID)))
                .thenReturn(List.of(comboDetail, itemDetail));
        when(movieQueryService.findMovieInfos(anyCollection())).thenReturn(Map.of(3L, buildMovieInfo()));
        when(cinemaQueryService.findHallInfos(anyCollection())).thenReturn(Map.of(
                5L, new HallInfoResponse(5L, CINEMA_ID, "Phòng 1", ScreenType.SCREEN_2D, HallStatus.ACTIVE, false)));

        FnbPickupQueueResponse fnbPickupQueueResponse = fnbPickupService.findQueue(STAFF_USER_ID, CINEMA_ID);

        assertEquals(1, fnbPickupQueueResponse.showtimes().size());
        FnbPickupShowtimeGroupResponse group = fnbPickupQueueResponse.showtimes().get(0);
        assertEquals("Phòng 1", group.hallName());
        assertEquals(1, group.pendingBookingCount());
        assertEquals(4, group.pendingQuantity());
        assertEquals(SHOWTIME_START.minusSeconds(3600), group.pickupOpensAt());
        assertEquals(2, group.bookings().get(0).lines().size());
        assertEquals(2, group.bookings().get(0).lines().get(1).remainingQuantity());
    }

    @Test
    void queueIsEmptyWhenNoShowtimeIsInTheHorizon() {
        when(showtimeQueryService.findActiveShowtimeInfosBetween(any(), any())).thenReturn(List.of());

        assertTrue(fnbPickupService.findQueue(STAFF_USER_ID, CINEMA_ID).showtimes().isEmpty());
        verify(bookingRepository, never()).findWithUndeliveredFnb(anyLong(), any(), any());
    }

    private FnbPickupScanResponse scan(String code) {
        return fnbPickupService.scan(STAFF_USER_ID, new FnbPickupScanRequest(code, CINEMA_ID));
    }

    private FnbPickupScanResponse deliver(List<FnbPickupDeliverLineRequest> lines) {
        return fnbPickupService.deliver(STAFF_USER_ID, new FnbPickupDeliverRequest(QR_CODE, CINEMA_ID, lines));
    }

    private BookingFnbDetail buildDetail(Long id, Long comboId, Long itemId, int quantity) {
        BookingFnbDetail bookingFnbDetail = new BookingFnbDetail();
        bookingFnbDetail.setId(id);
        bookingFnbDetail.setBookingId(BOOKING_ID);
        bookingFnbDetail.setComboId(comboId);
        bookingFnbDetail.setItemId(itemId);
        bookingFnbDetail.setQuantity(quantity);
        bookingFnbDetail.setUnitPrice(BigDecimal.valueOf(50000));
        return bookingFnbDetail;
    }

    private MovieInfoResponse buildMovieInfo() {
        return new MovieInfoResponse(
                3L, "Đứa con của thời tiết", "dua-con-cua-thoi-tiet", 120, AgeRating.P, null, MovieStatus.NOW_SHOWING, false);
    }

    private BookingDetailResponse buildBookingDetail() {
        BookingShowtimeResponse showtime = new BookingShowtimeResponse(
                20L, SHOWTIME_START, SHOWTIME_END, 3L, "dua-con-cua-thoi-tiet", "Đứa con của thời tiết",
                null, AgeRating.P, 120, CINEMA_ID, "Star Cineplex Bitexco", 5L, "Phòng 1", ScreenType.SCREEN_2D);
        return new BookingDetailResponse(
                BOOKING_ID, booking.getBookingCode(), booking.getStatus(), NOW, null, NOW, showtime, List.of(),
                List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), null);
    }
}
