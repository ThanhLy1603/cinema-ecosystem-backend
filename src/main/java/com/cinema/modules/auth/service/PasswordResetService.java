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
import com.cinema.modules.auth.security.TokenHasher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class PasswordResetService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);
    static final int MAX_REQUESTS_PER_WINDOW = 3;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OneTimeTokenService oneTimeTokenService;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public PasswordResetService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            OneTimeTokenService oneTimeTokenService,
            PasswordEncoder passwordEncoder,
            RateLimiter rateLimiter,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.oneTimeTokenService = oneTimeTokenService;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void requestReset(ForgotPasswordRequest forgotPasswordRequest, String ipAddress) {
        String email = forgotPasswordRequest.email().trim().toLowerCase(Locale.ROOT);
        String rateLimitKey = "forgot-password:" + TokenHasher.sha256Hex(email);
        if (!rateLimiter.tryAcquire(rateLimitKey, MAX_REQUESTS_PER_WINDOW, RATE_LIMIT_WINDOW)) {
            return;
        }

        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty() || !canResetPassword(optionalUser.get())) {
            return;
        }
        User user = optionalUser.get();
        String fullName = user.getFullName();

        String rawToken =
                oneTimeTokenService.issue(user, UserTokenType.PASSWORD_RESET, TOKEN_TTL, ipAddress, clock.instant());
        applicationEventPublisher.publishEvent(new PasswordResetRequestedEvent(email, fullName, rawToken));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest resetPasswordRequest) {
        Instant now = clock.instant();
        UserToken userToken = oneTimeTokenService
                .findUsableForUpdate(resetPasswordRequest.token(), UserTokenType.PASSWORD_RESET, now)
                .orElseThrow(PasswordResetService::invalidOrExpiredToken);
        User user = userToken.getUser();
        if (!canResetPassword(user)) {
            throw invalidOrExpiredToken();
        }
        AuthService.ensurePasswordFitsBcrypt(resetPasswordRequest.newPassword());

        user.setPasswordHash(passwordEncoder.encode(resetPasswordRequest.newPassword()));
        user.setPasswordChangedAt(now);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        user.setEmailVerified(true);
        userToken.markUsed(now);
        refreshTokenRepository.revokeAllByUserId(user.getId(), now);
    }

    private boolean canResetPassword(User user) {
        return user.getStatus() == UserStatus.ACTIVE || user.getStatus() == UserStatus.LOCKED;
    }

    private static AppException invalidOrExpiredToken() {
        return new AppException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn", HttpStatus.BAD_REQUEST,
                "INVALID_OR_EXPIRED_TOKEN");
    }
}
