package com.cinema.modules.auth.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.ratelimit.RateLimiter;
import com.cinema.modules.auth.dto.request.ForgotPasswordRequest;
import com.cinema.modules.auth.dto.request.ResetPasswordRequest;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.entity.UserToken;
import com.cinema.modules.auth.enums.UserStatus;
import com.cinema.modules.auth.enums.UserTokenType;
import com.cinema.modules.auth.event.PasswordResetRequestedEvent;
import com.cinema.modules.auth.repository.RefreshTokenRepository;
import com.cinema.modules.auth.repository.UserRepository;
import com.cinema.modules.auth.service.interfaces.IOneTimeTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private IOneTimeTokenService oneTimeTokenService;

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(
                userRepository, refreshTokenRepository, oneTimeTokenService, passwordEncoder, rateLimiter,
                applicationEventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(rateLimiter.tryAcquire(any(String.class), anyInt(), any(Duration.class))).thenReturn(true);
    }

    private User user(UserStatus status) {
        User user = new User();
        user.setId(7L);
        user.setEmail("an@cinema.vn");
        user.setFullName("Nguyễn An");
        user.setPasswordHash(passwordEncoder.encode("Matkhaucu123"));
        user.setStatus(status);
        return user;
    }

    private UserToken usableToken(User user) {
        UserToken userToken = new UserToken();
        userToken.setUser(user);
        userToken.setTokenType(UserTokenType.PASSWORD_RESET);
        userToken.setExpiresAt(NOW.plusSeconds(600));
        when(oneTimeTokenService.findUsableForUpdate("raw-token", UserTokenType.PASSWORD_RESET, NOW))
                .thenReturn(Optional.of(userToken));
        return userToken;
    }

    private void assertInvalidToken(ResetPasswordRequest resetPasswordRequest) {
        AppException appException =
                assertThrows(AppException.class, () -> passwordResetService.resetPassword(resetPasswordRequest));
        assertEquals(HttpStatus.BAD_REQUEST, appException.getStatus());
        assertEquals("INVALID_OR_EXPIRED_TOKEN", appException.getErrorCode());
    }

    @Test
    void requestResetIssuesAThirtyMinuteTokenAndPublishesTheEmailEvent() {
        User user = user(UserStatus.ACTIVE);
        when(userRepository.findByEmail("an@cinema.vn")).thenReturn(Optional.of(user));
        when(oneTimeTokenService.issue(user, UserTokenType.PASSWORD_RESET, Duration.ofMinutes(30), "10.0.0.1", NOW))
                .thenReturn("raw-token");

        passwordResetService.requestReset(new ForgotPasswordRequest("  An@Cinema.VN "), "10.0.0.1");

        ArgumentCaptor<PasswordResetRequestedEvent> captor =
                ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertEquals("an@cinema.vn", captor.getValue().email());
        assertEquals("Nguyễn An", captor.getValue().fullName());
        assertEquals("raw-token", captor.getValue().rawToken());
    }

    @Test
    void requestResetForUnknownEmailDoesNothingAndDoesNotFail() {
        when(userRepository.findByEmail("khong@cinema.vn")).thenReturn(Optional.empty());

        passwordResetService.requestReset(new ForgotPasswordRequest("khong@cinema.vn"), "10.0.0.1");

        verify(oneTimeTokenService, never())
                .issue(any(User.class), any(UserTokenType.class), any(Duration.class), any(), any(Instant.class));
        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void requestResetIsSilentlySkippedForSuspendedAndDeletedAccounts() {
        when(userRepository.findByEmail("an@cinema.vn"))
                .thenReturn(Optional.of(user(UserStatus.SUSPENDED)), Optional.of(user(UserStatus.DELETED)));

        passwordResetService.requestReset(new ForgotPasswordRequest("an@cinema.vn"), null);
        passwordResetService.requestReset(new ForgotPasswordRequest("an@cinema.vn"), null);

        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void temporarilyLockedAccountsCanStillRequestAReset() {
        User user = user(UserStatus.LOCKED);
        when(userRepository.findByEmail("an@cinema.vn")).thenReturn(Optional.of(user));
        when(oneTimeTokenService.issue(eq(user), eq(UserTokenType.PASSWORD_RESET), any(Duration.class), any(), any(Instant.class)))
                .thenReturn("raw-token");

        passwordResetService.requestReset(new ForgotPasswordRequest("an@cinema.vn"), null);

        verify(applicationEventPublisher).publishEvent(any(PasswordResetRequestedEvent.class));
    }

    @Test
    void requestResetOverTheHourlyLimitReturnsNormallyWithoutSendingAnything() {
        when(rateLimiter.tryAcquire(any(String.class), eq(3), eq(Duration.ofHours(1)))).thenReturn(false);

        passwordResetService.requestReset(new ForgotPasswordRequest("an@cinema.vn"), null);

        verify(userRepository, never()).findByEmail(any(String.class));
        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void resetPasswordChangesPasswordUnlocksVerifiesEmailAndRevokesEverySession() {
        User user = user(UserStatus.LOCKED);
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(NOW.plusSeconds(300));
        UserToken userToken = usableToken(user);

        passwordResetService.resetPassword(new ResetPasswordRequest("raw-token", "Matkhaumoi456"));

        assertTrue(passwordEncoder.matches("Matkhaumoi456", user.getPasswordHash()));
        assertEquals(NOW, user.getPasswordChangedAt());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertTrue(user.isEmailVerified());
        assertNotNull(userToken.getUsedAt());
        verify(refreshTokenRepository).revokeAllByUserId(7L, NOW);
    }

    @Test
    void googleOnlyAccountCanSetItsFirstPasswordThroughReset() {
        User user = user(UserStatus.ACTIVE);
        user.setPasswordHash(null);
        usableToken(user);

        passwordResetService.resetPassword(new ResetPasswordRequest("raw-token", "Matkhaumoi456"));

        assertTrue(user.hasPassword());
    }

    @Test
    void unknownExpiredOrUsedTokenIsRejectedWith400() {
        when(oneTimeTokenService.findUsableForUpdate("bad-token", UserTokenType.PASSWORD_RESET, NOW))
                .thenReturn(Optional.empty());

        assertInvalidToken(new ResetPasswordRequest("bad-token", "Matkhaumoi456"));

        verify(refreshTokenRepository, never()).revokeAllByUserId(any(Long.class), any(Instant.class));
    }

    @Test
    void tokenOfASuspendedAccountIsRejectedAndNothingChanges() {
        User user = user(UserStatus.SUSPENDED);
        String oldHash = user.getPasswordHash();
        UserToken userToken = usableToken(user);

        assertInvalidToken(new ResetPasswordRequest("raw-token", "Matkhaumoi456"));

        assertEquals(oldHash, user.getPasswordHash());
        assertNull(userToken.getUsedAt());
    }

    @Test
    void passwordLongerThan72BytesIsRejectedBeforeAnythingChanges() {
        User user = user(UserStatus.ACTIVE);
        String oldHash = user.getPasswordHash();
        UserToken userToken = usableToken(user);

        AppException appException = assertThrows(AppException.class, () ->
                passwordResetService.resetPassword(new ResetPasswordRequest("raw-token", "é1".repeat(30))));

        assertEquals("PASSWORD_TOO_LONG", appException.getErrorCode());
        assertEquals(oldHash, user.getPasswordHash());
        assertFalse(user.isEmailVerified());
        assertNull(userToken.getUsedAt());
    }
}
