package com.cinema.modules.auth.service;

import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.entity.UserToken;
import com.cinema.modules.auth.enums.UserTokenType;
import com.cinema.modules.auth.repository.UserTokenRepository;
import com.cinema.modules.auth.security.TokenHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OneTimeTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");

    @Mock
    private UserTokenRepository userTokenRepository;

    @Test
    void issueInvalidatesOldTokensThenStoresOnlyTheHashOfTheNewOne() {
        OneTimeTokenService oneTimeTokenService = new OneTimeTokenService(userTokenRepository);
        User user = new User();
        user.setId(7L);

        String rawToken = oneTimeTokenService.issue(
                user, UserTokenType.PASSWORD_RESET, Duration.ofMinutes(30), "10.0.0.1", NOW);

        InOrder inOrder = inOrder(userTokenRepository);
        inOrder.verify(userTokenRepository).invalidateUnused(7L, UserTokenType.PASSWORD_RESET, NOW);
        ArgumentCaptor<UserToken> captor = ArgumentCaptor.forClass(UserToken.class);
        inOrder.verify(userTokenRepository).save(captor.capture());
        UserToken savedUserToken = captor.getValue();
        assertEquals(TokenHasher.sha256Hex(rawToken), savedUserToken.getTokenHash());
        assertNotEquals(rawToken, savedUserToken.getTokenHash());
        assertEquals(UserTokenType.PASSWORD_RESET, savedUserToken.getTokenType());
        assertEquals(NOW.plus(Duration.ofMinutes(30)), savedUserToken.getExpiresAt());
        assertEquals("10.0.0.1", savedUserToken.getIpAddress());
        assertTrue(rawToken.length() >= 43);
    }

    @Test
    void findUsableLooksUpByHashAndRejectsUsedOrExpiredTokens() {
        OneTimeTokenService oneTimeTokenService = new OneTimeTokenService(userTokenRepository);
        UserToken usableUserToken = new UserToken();
        usableUserToken.setExpiresAt(NOW.plusSeconds(60));
        UserToken expiredUserToken = new UserToken();
        expiredUserToken.setExpiresAt(NOW.minusSeconds(1));
        UserToken usedUserToken = new UserToken();
        usedUserToken.setExpiresAt(NOW.plusSeconds(60));
        usedUserToken.markUsed(NOW.minusSeconds(5));
        when(userTokenRepository.findByTokenHashAndTypeForUpdate(
                TokenHasher.sha256Hex("good"), UserTokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(usableUserToken));
        when(userTokenRepository.findByTokenHashAndTypeForUpdate(
                TokenHasher.sha256Hex("old"), UserTokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(expiredUserToken));
        when(userTokenRepository.findByTokenHashAndTypeForUpdate(
                TokenHasher.sha256Hex("used"), UserTokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(usedUserToken));

        assertTrue(oneTimeTokenService.findUsableForUpdate(" good ", UserTokenType.EMAIL_VERIFICATION, NOW).isPresent());
        assertFalse(oneTimeTokenService.findUsableForUpdate("old", UserTokenType.EMAIL_VERIFICATION, NOW).isPresent());
        assertFalse(oneTimeTokenService.findUsableForUpdate("used", UserTokenType.EMAIL_VERIFICATION, NOW).isPresent());
        assertFalse(oneTimeTokenService.findUsableForUpdate("unknown", UserTokenType.EMAIL_VERIFICATION, NOW).isPresent());
    }

    @Test
    void blankTokenNeverReachesTheDatabase() {
        OneTimeTokenService oneTimeTokenService = new OneTimeTokenService(userTokenRepository);

        assertFalse(oneTimeTokenService.findUsableForUpdate("  ", UserTokenType.PASSWORD_RESET, NOW).isPresent());
        assertFalse(oneTimeTokenService.findUsableForUpdate(null, UserTokenType.PASSWORD_RESET, NOW).isPresent());

        verify(userTokenRepository, never()).findByTokenHashAndTypeForUpdate(any(String.class), any(UserTokenType.class));
    }
}
