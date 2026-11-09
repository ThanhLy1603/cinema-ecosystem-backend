package com.cinema.modules.auth.service;

import com.cinema.modules.auth.repository.RefreshTokenRepository;
import com.cinema.modules.auth.repository.UserTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenCleanupSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserTokenRepository userTokenRepository;

    @Test
    void deletesTokensThatExpiredMoreThanTheRetentionPeriodAgo() {
        TokenCleanupScheduler tokenCleanupScheduler = new TokenCleanupScheduler(
                refreshTokenRepository, userTokenRepository, Clock.fixed(NOW, ZoneOffset.UTC));

        tokenCleanupScheduler.deleteExpiredTokens();

        Instant expectedThreshold = NOW.minus(TokenCleanupScheduler.RETENTION_AFTER_EXPIRY);
        verify(refreshTokenRepository).deleteExpiredBefore(expectedThreshold);
        verify(userTokenRepository).deleteExpiredBefore(expectedThreshold);
    }
}
