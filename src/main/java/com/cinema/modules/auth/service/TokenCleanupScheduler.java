package com.cinema.modules.auth.service;

import com.cinema.modules.auth.repository.RefreshTokenRepository;
import com.cinema.modules.auth.repository.UserTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class TokenCleanupScheduler {

    static final Duration RETENTION_AFTER_EXPIRY = Duration.ofDays(1);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserTokenRepository userTokenRepository;
    private final Clock clock;

    public TokenCleanupScheduler(
            RefreshTokenRepository refreshTokenRepository,
            UserTokenRepository userTokenRepository,
            Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userTokenRepository = userTokenRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.token-cleanup.cron:0 0 3 * * *}")
    @Transactional
    public void deleteExpiredTokens() {
        Instant threshold = clock.instant().minus(RETENTION_AFTER_EXPIRY);
        int deletedRefreshTokens = refreshTokenRepository.deleteExpiredBefore(threshold);
        int deletedUserTokens = userTokenRepository.deleteExpiredBefore(threshold);
        log.info("Đã dọn {} refresh token và {} user token hết hạn", deletedRefreshTokens, deletedUserTokens);
    }
}
