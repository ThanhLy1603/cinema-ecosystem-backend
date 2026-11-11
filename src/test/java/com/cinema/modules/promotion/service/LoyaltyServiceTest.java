package com.cinema.modules.promotion.service;

import com.cinema.modules.auth.service.interfaces.IUserLoyaltyService;
import com.cinema.modules.promotion.entity.LoyaltyPointTransaction;
import com.cinema.modules.promotion.repository.LoyaltyPointTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoyaltyServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T03:00:00Z");

    @Mock
    private LoyaltyPointTransactionRepository loyaltyPointTransactionRepository;

    @Mock
    private IUserLoyaltyService userLoyaltyService;

    private LoyaltyService loyaltyService;

    @BeforeEach
    void setUp() {
        loyaltyService = new LoyaltyService(
                loyaltyPointTransactionRepository, userLoyaltyService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void earnsFivePercentOfThePaidAmountAtOnePointPerTenDongAndLogsTheBalance() {
        when(loyaltyPointTransactionRepository.existsByBookingIdAndTransactionType(100L, "EARN")).thenReturn(false);
        when(userLoyaltyService.addLoyaltyPoints(5L, 1075)).thenReturn(1575);

        int earnedPoints = loyaltyService.earnPointsForBooking(5L, 100L, new BigDecimal("215000"));

        assertEquals(1075, earnedPoints);
        ArgumentCaptor<LoyaltyPointTransaction> captor = ArgumentCaptor.forClass(LoyaltyPointTransaction.class);
        verify(loyaltyPointTransactionRepository).save(captor.capture());
        assertEquals(1075, captor.getValue().getPointsChange());
        assertEquals(1575, captor.getValue().getBalanceAfter());
        assertEquals("EARN", captor.getValue().getTransactionType());
        assertEquals(100L, captor.getValue().getBookingId());
        assertEquals(NOW, captor.getValue().getCreatedAt());
    }

    @Test
    void neverEarnsTwiceForTheSameBooking() {
        when(loyaltyPointTransactionRepository.existsByBookingIdAndTransactionType(100L, "EARN")).thenReturn(true);

        assertEquals(0, loyaltyService.earnPointsForBooking(5L, 100L, new BigDecimal("215000")));

        verify(userLoyaltyService, never()).addLoyaltyPoints(anyLong(), anyInt());
        verify(loyaltyPointTransactionRepository, never()).save(any(LoyaltyPointTransaction.class));
    }

    @Test
    void tinyAmountsThatRoundToZeroPointsAreIgnored() {
        when(loyaltyPointTransactionRepository.existsByBookingIdAndTransactionType(100L, "EARN")).thenReturn(false);

        assertEquals(0, loyaltyService.earnPointsForBooking(5L, 100L, new BigDecimal("100")));

        verify(userLoyaltyService, never()).addLoyaltyPoints(anyLong(), anyInt());
    }
}
