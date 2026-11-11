package com.cinema.modules.booking.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.booking.dto.request.BookingFnbLineRequest;
import com.cinema.modules.booking.dto.request.BookingFnbRequest;
import com.cinema.modules.booking.dto.response.BookingFnbResponse;
import com.cinema.modules.booking.dto.response.SeatHoldResponse;
import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.entity.BookingFnbDetail;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.repository.BookingFnbDetailRepository;
import com.cinema.modules.booking.repository.BookingRepository;
import com.cinema.modules.booking.service.interfaces.ISeatHoldService;
import com.cinema.modules.fnb.dto.response.FnbProductInfoResponse;
import com.cinema.modules.fnb.service.interfaces.IFnbQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingFnbServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final Long USER_ID = 5L;
    private static final Long SHOWTIME_ID = 20L;
    private static final Long BOOKING_ID = 100L;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingFnbDetailRepository bookingFnbDetailRepository;

    @Mock
    private IFnbQueryService fnbQueryService;

    @Mock
    private ISeatHoldService seatHoldService;

    @Mock
    private SeatGapChecker seatGapChecker;

    @Mock
    private PlatformTransactionManager platformTransactionManager;

    private final List<BookingFnbDetail> storedBookingFnbDetails = new ArrayList<>();
    private BookingFnbService bookingFnbService;
    private Booking booking;

    @BeforeEach
    void setUp() {
        bookingFnbService = new BookingFnbService(
                bookingRepository,
                bookingFnbDetailRepository,
                fnbQueryService,
                seatHoldService,
                seatGapChecker,
                new TransactionTemplate(platformTransactionManager),
                Clock.fixed(NOW, ZoneOffset.UTC));
        booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setUserId(USER_ID);
        booking.setShowtimeId(SHOWTIME_ID);
        booking.setStatus(BookingStatus.PENDING);
        booking.setExpiresAt(NOW.plusSeconds(300));
        lenient().when(platformTransactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(seatHoldService.getMyHold(USER_ID, SHOWTIME_ID)).thenReturn(new SeatHoldResponse(
                BOOKING_ID, "BK1", SHOWTIME_ID, booking.getExpiresAt(), NOW, List.of(), new BigDecimal("200000")));
        lenient().when(fnbQueryService.findComboInfos(any())).thenAnswer(invocationOnMock -> {
            java.util.Collection<Long> comboIds = invocationOnMock.getArgument(0);
            return comboIds.contains(1L)
                    ? Map.of(1L, new FnbProductInfoResponse(1L, "Combo 1", null, new BigDecimal("89000"), true))
                    : comboIds.contains(2L)
                    ? Map.of(2L, new FnbProductInfoResponse(2L, "Combo cũ", null, new BigDecimal("50000"), false))
                    : Map.of();
        });
        lenient().when(fnbQueryService.findItemInfos(any())).thenAnswer(invocationOnMock -> {
            java.util.Collection<Long> itemIds = invocationOnMock.getArgument(0);
            return itemIds.contains(7L)
                    ? Map.of(7L, new FnbProductInfoResponse(7L, "Nước suối", null, new BigDecimal("15000"), true))
                    : Map.of();
        });
        lenient().when(bookingFnbDetailRepository.saveAll(any())).thenAnswer(invocationOnMock -> {
            List<BookingFnbDetail> bookingFnbDetails = invocationOnMock.getArgument(0);
            storedBookingFnbDetails.addAll(bookingFnbDetails);
            return bookingFnbDetails;
        });
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    @Test
    void replaceStoresCurrentUnitPricesAndAddsFnbAmountToBookingTotal() {
        when(bookingRepository.findByUserIdAndStatusForUpdate(USER_ID, BookingStatus.PENDING))
                .thenReturn(Optional.of(booking));

        BookingFnbResponse bookingFnbResponse = bookingFnbService.replacePendingFnb(USER_ID, new BookingFnbRequest(List.of(
                new BookingFnbLineRequest(1L, null, 2),
                new BookingFnbLineRequest(null, 7L, 3))));

        assertEquals(new BigDecimal("223000"), bookingFnbResponse.fnbAmount());
        assertEquals(new BigDecimal("200000"), bookingFnbResponse.ticketAmount());
        assertEquals(new BigDecimal("423000"), bookingFnbResponse.totalAmount());
        assertEquals(new BigDecimal("423000"), booking.getTotalAmount());
        assertEquals(new BigDecimal("423000"), booking.getFinalAmount());
        assertEquals(2, storedBookingFnbDetails.size());
        assertEquals(new BigDecimal("89000"), storedBookingFnbDetails.get(0).getUnitPrice());
        assertEquals(1L, storedBookingFnbDetails.get(0).getComboId());
        assertNull(storedBookingFnbDetails.get(0).getItemId());
        assertEquals(BOOKING_ID, storedBookingFnbDetails.get(1).getBookingId());
        verify(bookingFnbDetailRepository).deleteAllByBookingId(BOOKING_ID);
    }

    @Test
    void replaceIsRejectedWhenTheSeatsLeaveAnOrphanSeatAndTouchesNothing() {
        when(bookingRepository.findByUserIdAndStatusForUpdate(USER_ID, BookingStatus.PENDING))
                .thenReturn(Optional.of(booking));
        org.mockito.Mockito.doThrow(new AppException("gap", HttpStatus.CONFLICT, "SEAT_GAP_LEFT"))
                .when(seatGapChecker).validateNoOrphanSeats(booking, NOW);

        AppException appException = assertThrows(AppException.class,
                () -> bookingFnbService.replacePendingFnb(USER_ID, new BookingFnbRequest(List.of())));

        assertEquals("SEAT_GAP_LEFT", appException.getErrorCode());
        verify(bookingFnbDetailRepository, never()).deleteAllByBookingId(anyLong());
    }

    @Test
    void replaceWithNoLinesClearsFnbAndKeepsTicketAmount() {
        when(bookingRepository.findByUserIdAndStatusForUpdate(USER_ID, BookingStatus.PENDING))
                .thenReturn(Optional.of(booking));

        BookingFnbResponse bookingFnbResponse =
                bookingFnbService.replacePendingFnb(USER_ID, new BookingFnbRequest(List.of()));

        assertEquals(BigDecimal.ZERO, bookingFnbResponse.fnbAmount());
        assertEquals(new BigDecimal("200000"), booking.getTotalAmount());
        assertEquals(0, storedBookingFnbDetails.size());
        verify(bookingFnbDetailRepository).deleteAllByBookingId(BOOKING_ID);
    }

    @Test
    void replaceIsRejectedWhenTheSameProductAppearsTwice() {
        when(bookingRepository.findByUserIdAndStatusForUpdate(USER_ID, BookingStatus.PENDING))
                .thenReturn(Optional.of(booking));

        AppException appException = assertThrows(AppException.class, () -> bookingFnbService.replacePendingFnb(
                USER_ID,
                new BookingFnbRequest(List.of(
                        new BookingFnbLineRequest(1L, null, 2), new BookingFnbLineRequest(1L, null, 1)))));

        assertEquals("FNB_DUPLICATE_LINE", appException.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, appException.getStatus());
        verify(bookingFnbDetailRepository, never()).deleteAllByBookingId(anyLong());
    }

    @Test
    void replaceIsRejectedWhenALineNamesBothComboAndItemOrNeither() {
        when(bookingRepository.findByUserIdAndStatusForUpdate(USER_ID, BookingStatus.PENDING))
                .thenReturn(Optional.of(booking));

        AppException bothAppException = assertThrows(AppException.class, () -> bookingFnbService.replacePendingFnb(
                USER_ID, new BookingFnbRequest(List.of(new BookingFnbLineRequest(1L, 7L, 1)))));
        AppException neitherAppException = assertThrows(AppException.class, () -> bookingFnbService.replacePendingFnb(
                USER_ID, new BookingFnbRequest(List.of(new BookingFnbLineRequest(null, null, 1)))));

        assertEquals("FNB_LINE_INVALID", bothAppException.getErrorCode());
        assertEquals("FNB_LINE_INVALID", neitherAppException.getErrorCode());
    }

    @Test
    void replaceIsRejectedWhenAProductIsNoLongerOrderableAndLeavesStoredLinesUntouched() {
        when(bookingRepository.findByUserIdAndStatusForUpdate(USER_ID, BookingStatus.PENDING))
                .thenReturn(Optional.of(booking));

        AppException appException = assertThrows(AppException.class, () -> bookingFnbService.replacePendingFnb(
                USER_ID, new BookingFnbRequest(List.of(new BookingFnbLineRequest(2L, null, 1)))));

        assertEquals("FNB_PRODUCT_UNAVAILABLE", appException.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, appException.getStatus());
        verify(bookingFnbDetailRepository, never()).deleteAllByBookingId(anyLong());
        verify(bookingFnbDetailRepository, never()).saveAll(any());
    }

    @Test
    void replaceIsRejectedWhenTheProductDoesNotExist() {
        when(bookingRepository.findByUserIdAndStatusForUpdate(USER_ID, BookingStatus.PENDING))
                .thenReturn(Optional.of(booking));

        AppException appException = assertThrows(AppException.class, () -> bookingFnbService.replacePendingFnb(
                USER_ID, new BookingFnbRequest(List.of(new BookingFnbLineRequest(null, 999L, 1)))));

        assertEquals("FNB_PRODUCT_NOT_FOUND", appException.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, appException.getStatus());
    }

    @Test
    void replaceIsRejectedWhenThereIsNoPendingBooking() {
        when(bookingRepository.findByUserIdAndStatusForUpdate(USER_ID, BookingStatus.PENDING))
                .thenReturn(Optional.empty());

        AppException appException = assertThrows(AppException.class,
                () -> bookingFnbService.replacePendingFnb(USER_ID, new BookingFnbRequest(List.of())));

        assertEquals("NO_PENDING_BOOKING", appException.getErrorCode());
    }

    @Test
    void replaceIsRejectedWhenThePendingBookingHasExpired() {
        booking.setExpiresAt(NOW.minusSeconds(1));
        when(bookingRepository.findByUserIdAndStatusForUpdate(USER_ID, BookingStatus.PENDING))
                .thenReturn(Optional.of(booking));

        AppException appException = assertThrows(AppException.class,
                () -> bookingFnbService.replacePendingFnb(USER_ID, new BookingFnbRequest(List.of())));

        assertEquals("NO_PENDING_BOOKING", appException.getErrorCode());
        verify(bookingFnbDetailRepository, never()).deleteAllByBookingId(anyLong());
    }

    @Test
    void getPendingFnbReturnsStoredLinesWithNamesAndSnapshotPrices() {
        BookingFnbDetail bookingFnbDetail = new BookingFnbDetail();
        bookingFnbDetail.setBookingId(BOOKING_ID);
        bookingFnbDetail.setItemId(7L);
        bookingFnbDetail.setQuantity(2);
        bookingFnbDetail.setUnitPrice(new BigDecimal("12000"));
        when(bookingRepository.findFirstByUserIdAndStatus(USER_ID, BookingStatus.PENDING))
                .thenReturn(Optional.of(booking));
        when(bookingFnbDetailRepository.findByBookingIdOrderByIdAsc(BOOKING_ID)).thenReturn(List.of(bookingFnbDetail));

        BookingFnbResponse bookingFnbResponse = bookingFnbService.getPendingFnb(USER_ID);

        assertEquals(BOOKING_ID, bookingFnbResponse.bookingId());
        assertEquals(booking.getExpiresAt(), bookingFnbResponse.expiresAt());
        assertEquals("Nước suối", bookingFnbResponse.lines().get(0).name());
        assertEquals(new BigDecimal("12000"), bookingFnbResponse.lines().get(0).unitPrice());
        assertEquals(new BigDecimal("24000"), bookingFnbResponse.fnbAmount());
        assertEquals(new BigDecimal("224000"), bookingFnbResponse.totalAmount());
    }

    @Test
    void getPendingFnbReturnsEmptyResponseWhenThereIsNoActivePendingBooking() {
        when(bookingRepository.findFirstByUserIdAndStatus(USER_ID, BookingStatus.PENDING))
                .thenReturn(Optional.empty());

        BookingFnbResponse bookingFnbResponse = bookingFnbService.getPendingFnb(USER_ID);

        assertNull(bookingFnbResponse.bookingId());
        assertEquals(0, bookingFnbResponse.lines().size());
        assertEquals(BigDecimal.ZERO, bookingFnbResponse.totalAmount());
    }
}
