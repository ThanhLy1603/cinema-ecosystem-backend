package com.cinema.modules.booking.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.ratelimit.RateLimiter;
import com.cinema.modules.audit.enums.AuditAction;
import com.cinema.modules.audit.service.interfaces.IAuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ScanGuard {

    static final int MAX_SCANS_PER_WINDOW = 60;
    static final int MAX_UNKNOWN_CODES_PER_WINDOW = 8;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimiter rateLimiter;
    private final IAuditLogService auditLogService;

    public ScanGuard(RateLimiter rateLimiter, IAuditLogService auditLogService) {
        this.rateLimiter = rateLimiter;
        this.auditLogService = auditLogService;
    }

    public void assertScanAllowed(Long staffUserId, Long cinemaId, String channel) {
        if (!rateLimiter.tryAcquire("scan:" + channel + ":" + staffUserId, MAX_SCANS_PER_WINDOW, WINDOW)) {
            throw tooManyScans(staffUserId, cinemaId, channel, null, "Quá " + MAX_SCANS_PER_WINDOW + " lượt quét trong 1 phút");
        }
    }

    public void registerUnknownCode(Long staffUserId, Long cinemaId, String channel, String scannedCode) {
        if (!rateLimiter.tryAcquire("scan-miss:" + channel + ":" + staffUserId, MAX_UNKNOWN_CODES_PER_WINDOW, WINDOW)) {
            throw tooManyScans(staffUserId, cinemaId, channel, scannedCode,
                    "Quá " + MAX_UNKNOWN_CODES_PER_WINDOW + " mã không tồn tại liên tiếp trong 1 phút");
        }
    }

    private AppException tooManyScans(
            Long staffUserId, Long cinemaId, String channel, String scannedCode, String reason) {
        auditLogService.record(
                staffUserId, cinemaId, AuditAction.SCAN_RATE_LIMITED, "SCAN_" + channel, scannedCode, reason);
        return new AppException(
                "Quét quá nhiều lần trong thời gian ngắn, vui lòng đợi 1 phút rồi thử lại",
                HttpStatus.TOO_MANY_REQUESTS,
                "SCAN_RATE_LIMITED");
    }
}
