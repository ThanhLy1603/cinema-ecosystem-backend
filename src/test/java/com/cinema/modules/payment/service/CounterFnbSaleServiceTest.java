package com.cinema.modules.payment.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.dto.response.UserContactResponse;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.auth.service.interfaces.IUserQueryService;
import com.cinema.modules.booking.dto.request.BookingFnbLineRequest;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.fnb.dto.response.FnbProductInfoResponse;
import com.cinema.modules.fnb.service.interfaces.IFnbQueryService;
import com.cinema.modules.payment.dto.request.CounterFnbCheckoutRequest;
import com.cinema.modules.payment.dto.response.CounterFnbReceiptResponse;
import com.cinema.modules.payment.entity.CounterFnbOrder;
import com.cinema.modules.payment.entity.CounterFnbOrderLine;
import com.cinema.modules.payment.enums.PaymentMethod;
import com.cinema.modules.payment.repository.CounterFnbOrderLineRepository;
import com.cinema.modules.payment.repository.CounterFnbOrderRepository;
import com.cinema.modules.audit.service.interfaces.IAuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CounterFnbSaleServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T04:00:00Z");
    private static final Long STAFF_USER_ID = 3L;
    private static final Long CINEMA_ID = 1L;

    @Mock
    private IAuditLogService auditLogService;

    @Mock
    private CounterFnbOrderRepository counterFnbOrderRepository;

    @Mock
    private CounterFnbOrderLineRepository counterFnbOrderLineRepository;

    @Mock
    private IFnbQueryService fnbQueryService;

    @Mock
    private IBranchAccessService branchAccessService;

    @Mock
    private IUserQueryService userQueryService;

    @Mock
    private ICinemaQueryService cinemaQueryService;

    private CounterFnbSaleService counterFnbSaleService;

    @BeforeEach
    void setUp() {
        counterFnbSaleService = new CounterFnbSaleService(
                counterFnbOrderRepository,
                counterFnbOrderLineRepository,
                fnbQueryService,
                branchAccessService,
                userQueryService,
                cinemaQueryService,
                auditLogService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(fnbQueryService.findComboInfos(anyCollection()))
                .thenReturn(Map.of(1L, new FnbProductInfoResponse(1L, "Combo đôi", null, new BigDecimal("109000"), true)));
        lenient().when(fnbQueryService.findItemInfos(anyCollection()))
                .thenReturn(Map.of(
                        5L, new FnbProductInfoResponse(5L, "Nước suối", null, new BigDecimal("20000"), true),
                        6L, new FnbProductInfoResponse(6L, "Bắp ngừng bán", null, new BigDecimal("40000"), false)));
        lenient().when(counterFnbOrderRepository.save(any(CounterFnbOrder.class))).thenAnswer(invocation -> {
            CounterFnbOrder counterFnbOrder = invocation.getArgument(0);
            counterFnbOrder.setId(50L);
            return counterFnbOrder;
        });
        lenient().when(counterFnbOrderLineRepository.saveAll(anyCollection()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(cinemaQueryService.findCinemaNames(anyCollection())).thenReturn(Map.of(CINEMA_ID, "Star Cineplex"));
        lenient().when(userQueryService.findContact(STAFF_USER_ID))
                .thenReturn(Optional.of(new UserContactResponse(STAFF_USER_ID, "an@cinema.vn", "Nguyễn Văn An")));
    }

    @Test
    void checkoutWithCashComputesTotalAndChange() {
        CounterFnbReceiptResponse counterFnbReceiptResponse = counterFnbSaleService.checkout(
                STAFF_USER_ID,
                request(PaymentMethod.CASH, new BigDecimal("200000"), line(1L, null, 1), line(null, 5L, 2)));

        assertEquals(new BigDecimal("149000"), counterFnbReceiptResponse.totalAmount());
        assertEquals(new BigDecimal("51000"), counterFnbReceiptResponse.changeAmount());
        assertEquals(2, counterFnbReceiptResponse.lines().size());
        assertTrue(counterFnbReceiptResponse.orderCode().startsWith("FB"));
        assertEquals("Nguyễn Văn An", counterFnbReceiptResponse.soldByStaffName());
        ArgumentCaptor<CounterFnbOrder> orderCaptor = ArgumentCaptor.forClass(CounterFnbOrder.class);
        verify(counterFnbOrderRepository).save(orderCaptor.capture());
        assertEquals(STAFF_USER_ID, orderCaptor.getValue().getSoldByStaffUserId());
        assertEquals(NOW, orderCaptor.getValue().getCreatedAt());
        verify(branchAccessService).assertCanAccessCinema(STAFF_USER_ID, CINEMA_ID);
    }

    @Test
    void checkoutWithCardStoresNoCashFields() {
        CounterFnbReceiptResponse counterFnbReceiptResponse = counterFnbSaleService.checkout(
                STAFF_USER_ID, request(PaymentMethod.CARD, null, line(null, 5L, 1)));

        assertEquals(null, counterFnbReceiptResponse.cashReceived());
        assertEquals(null, counterFnbReceiptResponse.changeAmount());
    }

    @Test
    void checkoutRejectsNotEnoughCash() {
        AppException appException = assertThrows(AppException.class, () -> counterFnbSaleService.checkout(
                STAFF_USER_ID, request(PaymentMethod.CASH, new BigDecimal("100000"), line(1L, null, 1))));

        assertEquals("CASH_NOT_ENOUGH", appException.getErrorCode());
        verify(counterFnbOrderRepository, never()).save(any());
    }

    @Test
    void checkoutRejectsUnavailableProduct() {
        AppException appException = assertThrows(AppException.class, () -> counterFnbSaleService.checkout(
                STAFF_USER_ID, request(PaymentMethod.CARD, null, line(null, 6L, 1))));

        assertEquals(HttpStatus.CONFLICT, appException.getStatus());
        assertEquals("FNB_PRODUCT_UNAVAILABLE", appException.getErrorCode());
    }

    @Test
    void checkoutRejectsDuplicateLine() {
        AppException appException = assertThrows(AppException.class, () -> counterFnbSaleService.checkout(
                STAFF_USER_ID, request(PaymentMethod.CARD, null, line(null, 5L, 1), line(null, 5L, 2))));

        assertEquals("FNB_DUPLICATE_LINE", appException.getErrorCode());
    }

    @Test
    void checkoutRejectsOnlinePaymentMethod() {
        AppException appException = assertThrows(AppException.class, () -> counterFnbSaleService.checkout(
                STAFF_USER_ID, request(PaymentMethod.VNPAY, null, line(null, 5L, 1))));

        assertEquals("PAYMENT_METHOD_INVALID", appException.getErrorCode());
    }

    @Test
    void findReceiptChecksBranchAccess() {
        CounterFnbOrder counterFnbOrder = new CounterFnbOrder();
        counterFnbOrder.setId(50L);
        counterFnbOrder.setOrderCode("FB123");
        counterFnbOrder.setCinemaId(CINEMA_ID);
        counterFnbOrder.setSoldByStaffUserId(STAFF_USER_ID);
        counterFnbOrder.setTotalAmount(new BigDecimal("20000"));
        counterFnbOrder.setPaymentMethod(PaymentMethod.CARD);
        counterFnbOrder.setCreatedAt(NOW);
        CounterFnbOrderLine counterFnbOrderLine = new CounterFnbOrderLine();
        counterFnbOrderLine.setItemId(5L);
        counterFnbOrderLine.setProductName("Nước suối");
        counterFnbOrderLine.setQuantity(1);
        counterFnbOrderLine.setUnitPrice(new BigDecimal("20000"));
        when(counterFnbOrderRepository.findByOrderCode("FB123")).thenReturn(Optional.of(counterFnbOrder));
        when(counterFnbOrderLineRepository.findByOrderIdOrderByIdAsc(50L)).thenReturn(List.of(counterFnbOrderLine));

        CounterFnbReceiptResponse counterFnbReceiptResponse = counterFnbSaleService.findReceipt(STAFF_USER_ID, "FB123");

        assertEquals("FB123", counterFnbReceiptResponse.orderCode());
        verify(branchAccessService).assertCanAccessCinema(STAFF_USER_ID, CINEMA_ID);
    }

    private CounterFnbCheckoutRequest request(
            PaymentMethod paymentMethod, BigDecimal cashReceived, BookingFnbLineRequest... lines) {
        return new CounterFnbCheckoutRequest(CINEMA_ID, paymentMethod, cashReceived, List.of(lines));
    }

    private BookingFnbLineRequest line(Long comboId, Long itemId, int quantity) {
        return new BookingFnbLineRequest(comboId, itemId, quantity);
    }
}
