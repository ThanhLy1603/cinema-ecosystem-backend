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
import com.cinema.modules.auth.service.interfaces.IEmailVerificationService;
import com.cinema.modules.auth.service.interfaces.IOneTimeTokenService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class EmailVerificationService implements IEmailVerificationService {

    static final Duration TOKEN_TTL = Duration.ofHours(24);
    static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);
    static final int MAX_RESENDS_PER_WINDOW = 3;

    private final UserRepository userRepository;
    private final IOneTimeTokenService oneTimeTokenService;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public EmailVerificationService(
            UserRepository userRepository,
            IOneTimeTokenService oneTimeTokenService,
            RateLimiter rateLimiter,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.userRepository = userRepository;
        this.oneTimeTokenService = oneTimeTokenService;
        this.rateLimiter = rateLimiter;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    @Transactional
    @Override
    public void sendVerification(Long userId, String ipAddress) {
        User user = loadUser(userId);
        if (user.isEmailVerified()) {
            return;
        }
        String email = user.getEmail();
        String fullName = user.getFullName();
        String rawToken = oneTimeTokenService.issue(
                user, UserTokenType.EMAIL_VERIFICATION, TOKEN_TTL, ipAddress, clock.instant());
        applicationEventPublisher.publishEvent(new EmailVerificationRequestedEvent(email, fullName, rawToken));
    }

    @Transactional
    @Override
    public void resendVerification(Long userId, String ipAddress) {
        User user = loadUser(userId);
        if (user.isEmailVerified()) {
            throw new AppException("Email của bạn đã được xác thực", HttpStatus.CONFLICT, "EMAIL_ALREADY_VERIFIED");
        }
        if (!rateLimiter.tryAcquire("resend-verification:" + userId, MAX_RESENDS_PER_WINDOW, RATE_LIMIT_WINDOW)) {
            throw new AppException("Bạn đã yêu cầu gửi lại quá nhiều lần, vui lòng thử lại sau",
                    HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS");
        }
        sendVerification(userId, ipAddress);
    }

    @Transactional
    @Override
    public void verifyEmail(VerifyEmailRequest verifyEmailRequest) {
        Instant now = clock.instant();
        UserToken userToken = oneTimeTokenService
                .findUsableForUpdate(verifyEmailRequest.token(), UserTokenType.EMAIL_VERIFICATION, now)
                .orElseThrow(EmailVerificationService::invalidOrExpiredToken);
        User user = userToken.getUser();
        if (user.getStatus() == UserStatus.DELETED) {
            throw invalidOrExpiredToken();
        }
        user.setEmailVerified(true);
        userToken.markUsed(now);
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() != UserStatus.DELETED)
                .orElseThrow(() -> new AppException("Phiên đăng nhập không hợp lệ hoặc đã hết hạn",
                        HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"));
    }

    private static AppException invalidOrExpiredToken() {
        return new AppException("Liên kết xác thực email không hợp lệ hoặc đã hết hạn", HttpStatus.BAD_REQUEST,
                "INVALID_OR_EXPIRED_TOKEN");
    }
}
