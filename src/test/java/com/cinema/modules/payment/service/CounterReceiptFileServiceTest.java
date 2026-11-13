package com.cinema.modules.payment.service;

import com.cinema.common.exception.AppException;
import com.cinema.config.properties.ReceiptProperties;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.booking.dto.response.BookingDetailResponse;
import com.cinema.modules.booking.dto.response.BookingFnbLineResponse;
import com.cinema.modules.booking.dto.response.BookingShowtimeResponse;
import com.cinema.modules.booking.dto.response.BookingTicketResponse;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.service.interfaces.IBookingDetailService;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.payment.dto.response.CounterFnbReceiptLineResponse;
import com.cinema.modules.payment.dto.response.CounterFnbReceiptResponse;
import com.cinema.modules.payment.dto.response.CounterReceiptFileResponse;
import com.cinema.modules.payment.entity.Payment;
import com.cinema.modules.payment.enums.PaymentMethod;
import com.cinema.modules.payment.repository.PaymentRepository;
import com.cinema.modules.payment.service.interfaces.ICounterFnbSaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CounterReceiptFileServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T04:12:00Z");
    private static final Long STAFF_USER_ID = 3L;
    private static final Long CINEMA_ID = 1L;
    private static final String BOOKING_CODE = "BK123";

    @TempDir
    Path receiptDirectory;

    @Mock
    private IBookingDetailService bookingDetailService;

    @Mock
    private IBranchAccessService branchAccessService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ICounterFnbSaleService counterFnbSaleService;

    private CounterReceiptFileService counterReceiptFileService;

    @BeforeEach
    void setUp() {
        counterReceiptFileService = new CounterReceiptFileService(
                bookingDetailService,
                branchAccessService,
                paymentRepository,
                new CounterReceiptPdfRenderer(),
                counterFnbSaleService,
                new ReceiptProperties(receiptDirectory.resolve("Receipt_Cinema").toString()));
    }

    @Test
    void saveReceiptFileWritesPdfNamedByBookingCode() throws IOException {
        when(bookingDetailService.findBookingDetailByCode(BOOKING_CODE)).thenReturn(Optional.of(bookingDetail()));
        when(paymentRepository.findByTransactionRef(BOOKING_CODE + "-POS")).thenReturn(Optional.of(payment()));

        CounterReceiptFileResponse counterReceiptFileResponse =
                counterReceiptFileService.saveReceiptFile(STAFF_USER_ID, BOOKING_CODE);

        Path receiptFile = receiptDirectory.resolve("Receipt_Cinema").resolve("BK123.pdf");
        assertEquals("BK123.pdf", counterReceiptFileResponse.fileName());
        assertTrue(Files.exists(receiptFile));
        byte[] receiptBytes = Files.readAllBytes(receiptFile);
        assertEquals("%PDF", new String(receiptBytes, 0, 4, StandardCharsets.US_ASCII));
        verify(branchAccessService).assertCanAccessCinema(STAFF_USER_ID, CINEMA_ID);
    }

    @Test
    void saveReceiptFileRejectsOnlineBooking() {
        when(bookingDetailService.findBookingDetailByCode(BOOKING_CODE)).thenReturn(Optional.of(bookingDetail()));
        when(paymentRepository.findByTransactionRef(BOOKING_CODE + "-POS")).thenReturn(Optional.empty());

        AppException appException = assertThrows(
                AppException.class, () -> counterReceiptFileService.saveReceiptFile(STAFF_USER_ID, BOOKING_CODE));

        assertEquals(HttpStatus.NOT_FOUND, appException.getStatus());
    }

    @Test
    void saveFnbReceiptFileWritesPdfNamedByOrderCode() throws IOException {
        when(counterFnbSaleService.findReceipt(STAFF_USER_ID, "FB123")).thenReturn(new CounterFnbReceiptResponse(
                "FB123", CINEMA_ID, "Star Cineplex Bitexco",
                List.of(new CounterFnbReceiptLineResponse(1L, null, "Combo đôi", 1, new BigDecimal("109000"),
                        new BigDecimal("109000"))),
                new BigDecimal("109000"), PaymentMethod.CASH, new BigDecimal("200000"), new BigDecimal("91000"),
                "Nguyễn Văn An", NOW));

        CounterReceiptFileResponse counterReceiptFileResponse =
                counterReceiptFileService.saveFnbReceiptFile(STAFF_USER_ID, "FB123");

        Path receiptFile = receiptDirectory.resolve("Receipt_Cinema").resolve("FB123.pdf");
        assertEquals("FB123.pdf", counterReceiptFileResponse.fileName());
        byte[] receiptBytes = Files.readAllBytes(receiptFile);
        assertEquals("%PDF", new String(receiptBytes, 0, 4, StandardCharsets.US_ASCII));
    }

    private Payment payment() {
        Payment payment = new Payment();
        payment.setPaymentMethod(PaymentMethod.CASH);
        payment.setPaidAt(NOW);
        payment.setResponseData(Map.of(
                "cashReceived", "800000",
                "changeAmount", "82000",
                "staffName", "Nguyễn Văn An",
                "customerName", "Lê Thị Bình"));
        return payment;
    }

    private BookingDetailResponse bookingDetail() {
        BookingShowtimeResponse bookingShowtimeResponse = new BookingShowtimeResponse(
                20L, NOW, NOW.plusSeconds(7200), 3L, "phim", "Đứa con của thời tiết", null, AgeRating.P, 120,
                CINEMA_ID, "Star Cineplex Bitexco", 5L, "Phòng 1", ScreenType.SCREEN_2D);
        return new BookingDetailResponse(
                100L, BOOKING_CODE, BookingStatus.PAID, NOW, null, NOW, bookingShowtimeResponse, List.of(),
                List.of(
                        new BookingFnbLineResponse(1L, null, "Combo đôi", null, 1, new BigDecimal("109000"),
                                new BigDecimal("109000"), 0, null),
                        new BookingFnbLineResponse(2L, null, "Combo gia đình", null, 1, new BigDecimal("249000"),
                                new BigDecimal("249000"), 0, null)),
                new BigDecimal("360000"), new BigDecimal("358000"), BigDecimal.ZERO, new BigDecimal("718000"),
                new BigDecimal("718000"),
                List.of(
                        new BookingTicketResponse(1L, "T1", 1L, "A1", new BigDecimal("90000"), false, null),
                        new BookingTicketResponse(2L, "T2", 2L, "A2", new BigDecimal("90000"), false, null),
                        new BookingTicketResponse(3L, "T3", 3L, "B1", new BigDecimal("90000"), false, null),
                        new BookingTicketResponse(4L, "T4", 4L, "B2", new BigDecimal("90000"), false, null)),
                null);
    }
}
