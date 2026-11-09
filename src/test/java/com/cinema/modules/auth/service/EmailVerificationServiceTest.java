package com.cinema.modules.auth.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.ratelimit.RateLimiter;
import com.cinema.modules.auth.dto.request.VerifyEmailRequest;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.entity.UserToken;
import com.cinema.modules.auth.enums.UserStatus;
import com.cinema.modules.auth.enums.UserTokenType;
import com.cinema.modules.auth.event.EmailVerificationRequestedEvent;
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
class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");
    private static final Long USER_ID = 7L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IOneTimeTokenService oneTimeTokenService;

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        emailVerificationService = new EmailVerificationService(
                userRepository, oneTimeTokenService, rateLimiter, applicationEventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(rateLimiter.tryAcquire(any(String.class), anyInt(), any(Duration.class))).thenReturn(true);
    }

    private User user(boolean emailVerified, UserStatus status) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("an@cinema.vn");
        user.setFullName("Nguyễn An");
        user.setEmailVerified(emailVerified);
        user.setStatus(status);
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }

    private UserToken usableToken(User user) {
        UserToken userToken = new UserToken();
        userToken.setUser(user);
        userToken.setExpiresAt(NOW.plusSeconds(600));
        when(oneTimeTokenService.findUsableForUpdate("raw-token", UserTokenType.EMAIL_VERIFICATION, NOW))
                .thenReturn(Optional.of(userToken));
        return userToken;
    }

    @Test
    void sendVerificationIssuesA24HourTokenAndPublishesTheEmailEvent() {
        User user = user(false, UserStatus.ACTIVE);
        when(oneTimeTokenService.issue(user, UserTokenType.EMAIL_VERIFICATION, Duration.ofHours(24), "10.0.0.1", NOW))
                .thenReturn("raw-token");

        emailVerificationService.sendVerification(USER_ID, "10.0.0.1");

        ArgumentCaptor<EmailVerificationRequestedEvent> captor =
                ArgumentCaptor.forClass(EmailVerificationRequestedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertEquals("an@cinema.vn", captor.getValue().email());
        assertEquals("raw-token", captor.getValue().rawToken());
    }

    @Test
    void sendVerificationDoesNothingForAnAlreadyVerifiedEmail() {
        user(true, UserStatus.ACTIVE);

        emailVerificationService.sendVerification(USER_ID, null);

        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void resendRejectsAlreadyVerifiedAccountsWith409() {
        user(true, UserStatus.ACTIVE);

        AppException appException =
                assertThrows(AppException.class, () -> emailVerificationService.resendVerification(USER_ID, null));

        assertEquals(HttpStatus.CONFLICT, appException.getStatus());
        assertEquals("EMAIL_ALREADY_VERIFIED", appException.getErrorCode());
    }

    @Test
    void resendIsLimitedToThreePerHourPerUserAndReturns429() {
        user(false, UserStatus.ACTIVE);
        when(rateLimiter.tryAcquire("resend-verification:7", 3, Duration.ofHours(1))).thenReturn(false);

        AppException appException =
                assertThrows(AppException.class, () -> emailVerificationService.resendVerification(USER_ID, null));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, appException.getStatus());
        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void resendSendsANewEmailWhenWithinTheLimit() {
        User user = user(false, UserStatus.ACTIVE);
        when(oneTimeTokenService.issue(eq(user), eq(UserTokenType.EMAIL_VERIFICATION), any(Duration.class), any(), any(Instant.class)))
                .thenReturn("raw-token");

        emailVerificationService.resendVerification(USER_ID, null);

        verify(applicationEventPublisher).publishEvent(any(EmailVerificationRequestedEvent.class));
    }

    @Test
    void verifyEmailMarksTheEmailVerifiedAndConsumesTheToken() {
        User user = user(false, UserStatus.ACTIVE);
        UserToken userToken = usableToken(user);

        emailVerificationService.verifyEmail(new VerifyEmailRequest("raw-token"));

        assertTrue(user.isEmailVerified());
        assertNotNull(userToken.getUsedAt());
    }

    @Test
    void verifyEmailRejectsUnknownExpiredOrUsedTokensWith400() {
        when(oneTimeTokenService.findUsableForUpdate("bad", UserTokenType.EMAIL_VERIFICATION, NOW))
                .thenReturn(Optional.empty());

        AppException appException = assertThrows(AppException.class,
                () -> emailVerificationService.verifyEmail(new VerifyEmailRequest("bad")));

        assertEquals(HttpStatus.BAD_REQUEST, appException.getStatus());
        assertEquals("INVALID_OR_EXPIRED_TOKEN", appException.getErrorCode());
    }

    @Test
    void verifyEmailRejectsDeletedAccounts() {
        User user = user(false, UserStatus.DELETED);
        UserToken userToken = usableToken(user);

        assertThrows(AppException.class, () -> emailVerificationService.verifyEmail(new VerifyEmailRequest("raw-token")));

        assertFalse(user.isEmailVerified());
        assertNull(userToken.getUsedAt());
    }
}
