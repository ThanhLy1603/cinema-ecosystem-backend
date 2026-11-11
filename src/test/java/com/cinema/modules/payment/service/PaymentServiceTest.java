package com.cinema.modules.payment.service;

import com.cinema.common.exception.AppException;
import com.cinema.config.properties.VnpayProperties;
import com.cinema.modules.booking.dto.response.PayableBookingResponse;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.enums.SettlementOutcome;
import com.cinema.modules.booking.service.interfaces.IBookingSettlementService;
import com.cinema.modules.payment.dto.request.SimulatePaymentRequest;
import com.cinema.modules.payment.dto.response.PaymentUrlResponse;
import com.cinema.modules.payment.dto.response.VnpayIpnResponse;
import com.cinema.modules.payment.entity.Payment;
import com.cinema.modules.payment.enums.PaymentStatus;
import com.cinema.modules.payment.enums.SimulatedPaymentOutcome;
import com.cinema.modules.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T03:00:00Z");
    private static final Long USER_ID = 5L;
    private static final Long BOOKING_ID = 100L;
    private static final String TXN_REF = "BK123-1762830000";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private IBookingSettlementService bookingSettlementService;

    @Mock
    private PlatformTransactionManager platformTransactionManager;

    private VnpaySigner vnpaySigner;
    private PaymentService paymentService;
    private Payment payment;

    @BeforeEach
    void setUp() {
        buildService(new VnpayProperties("TMN", "secret-key", "https://pay.example/vpcpay", "http://localhost/return", 15, true));
        payment = new Payment();
        payment.setBookingId(BOOKING_ID);
        payment.setTransactionRef(TXN_REF);
        payment.setAmount(new BigDecimal("180000.00"));
        payment.setStatus(PaymentStatus.PENDING);
        lenient().when(platformTransactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(paymentRepository.findByTransactionRef(TXN_REF)).thenReturn(Optional.of(payment));
        lenient().when(paymentRepository.findByTransactionRefForUpdate(TXN_REF)).thenReturn(Optional.of(payment));
        lenient().when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        lenient().when(bookingSettlementService.findOwnedBookingStatus(BOOKING_ID, USER_ID))
                .thenReturn(Optional.of(BookingStatus.PENDING));
    }

    private void buildService(VnpayProperties vnpayProperties) {
        vnpaySigner = new VnpaySigner(vnpayProperties);
        paymentService = new PaymentService(
                paymentRepository,
                bookingSettlementService,
                vnpaySigner,
                vnpayProperties,
                new TransactionTemplate(platformTransactionManager),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Map<String, String> ipnParams(String responseCode, String amount) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", TXN_REF);
        params.put("vnp_Amount", amount);
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionStatus", responseCode);
        params.put("vnp_SecureHash", vnpaySigner.sign(params));
        return params;
    }

    @Test
    void ipnWithABadSignatureIsRejectedWithoutTouchingAnything() {
        Map<String, String> params = ipnParams("00", "18000000");
        params.put("vnp_Amount", "1");

        VnpayIpnResponse vnpayIpnResponse = paymentService.processVnpayIpn(params);

        assertEquals("97", vnpayIpnResponse.rspCode());
        verify(bookingSettlementService, never()).settlePaidBooking(anyLong());
    }

    @Test
    void ipnForAnUnknownTransactionAnswersOrderNotFound() {
        when(paymentRepository.findByTransactionRef(TXN_REF)).thenReturn(Optional.empty());

        assertEquals("01", paymentService.processVnpayIpn(ipnParams("00", "18000000")).rspCode());
    }

    @Test
    void ipnWithAnAmountThatDiffersFromThePaymentIsRejected() {
        VnpayIpnResponse vnpayIpnResponse = paymentService.processVnpayIpn(ipnParams("00", "17000000"));

        assertEquals("04", vnpayIpnResponse.rspCode());
        verify(bookingSettlementService, never()).settlePaidBooking(anyLong());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    @Test
    void successfulIpnSettlesTheBookingAndMarksThePaymentSuccessful() {
        when(bookingSettlementService.settlePaidBooking(BOOKING_ID)).thenReturn(SettlementOutcome.PAID);

        VnpayIpnResponse vnpayIpnResponse = paymentService.processVnpayIpn(ipnParams("00", "18000000"));

        assertEquals("00", vnpayIpnResponse.rspCode());
        assertEquals("Confirm Success", vnpayIpnResponse.message());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(NOW, payment.getPaidAt());
        assertNotNull(payment.getResponseData());
    }

    @Test
    void repeatedIpnAnswersOkEveryTimeAndKeepsTheFirstPaymentRecord() {
        when(bookingSettlementService.settlePaidBooking(BOOKING_ID))
                .thenReturn(SettlementOutcome.PAID, SettlementOutcome.ALREADY_PAID, SettlementOutcome.ALREADY_PAID);

        VnpayIpnResponse firstResponse = paymentService.processVnpayIpn(ipnParams("00", "18000000"));
        Instant firstPaidAt = payment.getPaidAt();
        VnpayIpnResponse secondResponse = paymentService.processVnpayIpn(ipnParams("00", "18000000"));
        VnpayIpnResponse thirdResponse = paymentService.processVnpayIpn(ipnParams("00", "18000000"));

        assertEquals("Confirm Success", firstResponse.message());
        assertEquals("00", secondResponse.rspCode());
        assertEquals("Order already confirmed", secondResponse.message());
        assertEquals("Order already confirmed", thirdResponse.message());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(firstPaidAt, payment.getPaidAt());
        verify(paymentRepository, times(1)).save(payment);
    }

    @Test
    void ipnForABookingThatLostItsSeatsStillRecordsThePaymentButReportsNoTickets() {
        when(bookingSettlementService.settlePaidBooking(BOOKING_ID)).thenReturn(SettlementOutcome.REFUND_REQUIRED);

        VnpayIpnResponse vnpayIpnResponse = paymentService.processVnpayIpn(ipnParams("00", "18000000"));

        assertEquals("00", vnpayIpnResponse.rspCode());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
    }

    @Test
    void failedGatewayResultMarksThePaymentFailedAndNeverSettlesTheBooking() {
        VnpayIpnResponse vnpayIpnResponse = paymentService.processVnpayIpn(ipnParams("24", "18000000"));

        assertEquals("00", vnpayIpnResponse.rspCode());
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertNull(payment.getPaidAt());
        verify(bookingSettlementService, never()).settlePaidBooking(anyLong());
    }

    @Test
    void aLateFailureDoesNotOverwriteAnAlreadySuccessfulPayment() {
        payment.setStatus(PaymentStatus.SUCCESS);

        paymentService.processVnpayIpn(ipnParams("24", "18000000"));

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createPaymentBuildsASignedUrlWithAmountTimesOneHundredAndExpiryBeforeTheHold() {
        when(bookingSettlementService.requirePayableBooking(USER_ID)).thenReturn(new PayableBookingResponse(
                BOOKING_ID, "BK123", new BigDecimal("180000.00"), NOW.plusSeconds(600)));

        PaymentUrlResponse paymentUrlResponse = paymentService.createVnpayPayment(USER_ID, "203.0.113.9");

        assertTrue(paymentUrlResponse.paymentUrl().contains("vnp_Amount=18000000"));
        assertTrue(paymentUrlResponse.paymentUrl().contains("vnp_TxnRef=BK123-" + NOW.getEpochSecond()));
        assertTrue(paymentUrlResponse.paymentUrl().contains("vnp_IpAddr=203.0.113.9"));
        assertEquals(NOW.plusSeconds(540), paymentUrlResponse.expiresAt());
        assertTrue(paymentUrlResponse.paymentUrl().contains("vnp_ExpireDate=20261111100900"));
        Map<String, String> queryParams = new HashMap<>();
        for (String pair : paymentUrlResponse.paymentUrl().substring(paymentUrlResponse.paymentUrl().indexOf('?') + 1).split("&")) {
            String[] keyAndValue = pair.split("=", 2);
            queryParams.put(keyAndValue[0], java.net.URLDecoder.decode(keyAndValue[1], java.nio.charset.StandardCharsets.UTF_8));
        }
        assertTrue(vnpaySigner.verify(queryParams));
    }

    @Test
    void createPaymentIsRefusedWhenLessThanTwoMinutesOfHoldRemain() {
        when(bookingSettlementService.requirePayableBooking(USER_ID)).thenReturn(new PayableBookingResponse(
                BOOKING_ID, "BK123", new BigDecimal("180000.00"), NOW.plusSeconds(90)));

        AppException appException =
                assertThrows(AppException.class, () -> paymentService.createVnpayPayment(USER_ID, "1.2.3.4"));

        assertEquals("HOLD_EXPIRING_SOON", appException.getErrorCode());
        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void createPaymentIsRefusedWhenTheGatewayIsNotConfigured() {
        buildService(new VnpayProperties("", "", "https://pay.example", "http://localhost/return", 15, false));

        AppException appException =
                assertThrows(AppException.class, () -> paymentService.createVnpayPayment(USER_ID, "1.2.3.4"));

        assertEquals("PAYMENT_NOT_CONFIGURED", appException.getErrorCode());
    }

    @Test
    void simulatorRunsTheRealIpnPathWithASignedCallback() {
        when(bookingSettlementService.settlePaidBooking(BOOKING_ID)).thenReturn(SettlementOutcome.PAID);

        VnpayIpnResponse vnpayIpnResponse = paymentService.simulateGatewayCallback(
                USER_ID, new SimulatePaymentRequest(TXN_REF, SimulatedPaymentOutcome.SUCCESS));

        assertEquals("00", vnpayIpnResponse.rspCode());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
    }

    @Test
    void simulatorIsHiddenWhenDisabledAndRefusesPaymentsOfOtherUsers() {
        AppException otherUserException = assertThrows(AppException.class, () -> paymentService.simulateGatewayCallback(
                99L, new SimulatePaymentRequest(TXN_REF, SimulatedPaymentOutcome.SUCCESS)));
        assertEquals("PAYMENT_NOT_FOUND", otherUserException.getErrorCode());

        buildService(new VnpayProperties("TMN", "secret-key", "https://pay.example", "http://localhost/return", 15, false));
        AppException disabledException = assertThrows(AppException.class, () -> paymentService.simulateGatewayCallback(
                USER_ID, new SimulatePaymentRequest(TXN_REF, SimulatedPaymentOutcome.SUCCESS)));
        assertEquals("SIMULATOR_DISABLED", disabledException.getErrorCode());
    }
}
