package com.cinema.modules.booking.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.ratelimit.RateLimiter;
import com.cinema.modules.audit.enums.AuditAction;
import com.cinema.modules.audit.service.interfaces.IAuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanGuardTest {

    private static final Long STAFF_USER_ID = 3L;
    private static final Long CINEMA_ID = 1L;

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private IAuditLogService auditLogService;

    private ScanGuard scanGuard;

    @BeforeEach
    void setUp() {
        scanGuard = new ScanGuard(rateLimiter, auditLogService);
    }

    @Test
    void assertScanAllowedPassesWhenUnderLimit() {
        when(rateLimiter.tryAcquire(eq("scan:CHECK_IN:3"), anyInt(), any())).thenReturn(true);

        assertDoesNotThrow(() -> scanGuard.assertScanAllowed(STAFF_USER_ID, CINEMA_ID, "CHECK_IN"));

        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void assertScanAllowedThrowsTooManyRequestsAndAuditsWhenOverLimit() {
        when(rateLimiter.tryAcquire(startsWith("scan:"), anyInt(), any())).thenReturn(false);

        AppException appException = assertThrows(
                AppException.class, () -> scanGuard.assertScanAllowed(STAFF_USER_ID, CINEMA_ID, "CHECK_IN"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, appException.getStatus());
        assertEquals("SCAN_RATE_LIMITED", appException.getErrorCode());
        verify(auditLogService).record(
                eq(STAFF_USER_ID), eq(CINEMA_ID), eq(AuditAction.SCAN_RATE_LIMITED), eq("SCAN_CHECK_IN"), any(), any());
    }

    @Test
    void registerUnknownCodeLocksAfterTooManyMisses() {
        when(rateLimiter.tryAcquire(eq("scan-miss:FNB_PICKUP:3"), eq(ScanGuard.MAX_UNKNOWN_CODES_PER_WINDOW), any()))
                .thenReturn(false);

        AppException appException = assertThrows(AppException.class,
                () -> scanGuard.registerUnknownCode(STAFF_USER_ID, CINEMA_ID, "FNB_PICKUP", "ZZ9"));

        assertEquals("SCAN_RATE_LIMITED", appException.getErrorCode());
        verify(auditLogService).record(
                eq(STAFF_USER_ID), eq(CINEMA_ID), eq(AuditAction.SCAN_RATE_LIMITED), eq("SCAN_FNB_PICKUP"), eq("ZZ9"),
                any());
    }
}
