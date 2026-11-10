package com.cinema.modules.booking.service;

import com.cinema.modules.booking.service.interfaces.ISeatHoldExpiryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SeatHoldExpiryScheduler {

    private final ISeatHoldExpiryService seatHoldExpiryService;

    public SeatHoldExpiryScheduler(ISeatHoldExpiryService seatHoldExpiryService) {
        this.seatHoldExpiryService = seatHoldExpiryService;
    }

    @Scheduled(fixedDelayString = "${app.seat-hold.expiry-scan-interval-ms:10000}")
    public void releaseExpiredHolds() {
        try {
            int releasedCount = seatHoldExpiryService.releaseExpiredHolds();
            if (releasedCount > 0) {
                log.info("Đã giải phóng {} ghế hết hạn giữ", releasedCount);
            }
        } catch (RuntimeException runtimeException) {
            log.error("Không thể giải phóng ghế hết hạn", runtimeException);
        }
    }
}
