package com.cinema.modules.auth.service;

import com.cinema.common.exception.AppException;
import com.cinema.config.JwtProperties;
import com.cinema.modules.auth.dto.request.LoginRequest;
import com.cinema.modules.auth.dto.request.RegisterRequest;
import com.cinema.modules.auth.dto.response.UserResponse;
import com.cinema.modules.auth.entity.RefreshToken;
import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.enums.UserStatus;
import com.cinema.modules.auth.model.AuthSession;
import com.cinema.modules.auth.model.ClientInfo;
import com.cinema.modules.auth.repository.RefreshTokenRepository;
import com.cinema.modules.auth.repository.RoleRepository;
import com.cinema.modules.auth.repository.UserRepository;
import com.cinema.modules.auth.security.JwtTokenProvider;
import com.cinema.modules.auth.security.TokenHasher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    static final Duration REUSE_GRACE_PERIOD = Duration.ofSeconds(10);
    public static final String REFRESH_IN_PROGRESS_ERROR_CODE = "REFRESH_IN_PROGRESS";
    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;
    private static final int DEVICE_INFO_MAX_LENGTH = 255;
    private static final int IP_ADDRESS_MAX_LENGTH = 45;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final Duration refreshTokenTtl;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            JwtProperties jwtProperties,
            Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenTtl = Duration.ofSeconds(jwtProperties.refreshTokenExpirationSeconds());
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest registerRequest) {
        String email = normalizeEmail(registerRequest.email());
        String phone = normalizePhone(registerRequest.phone());
        ensurePasswordFitsBcrypt(registerRequest.password());

        if (userRepository.existsByEmail(email)) {
            throw new AppException("Email đã được sử dụng", HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS");
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new AppException("Số điện thoại đã được sử dụng", HttpStatus.CONFLICT, "PHONE_ALREADY_EXISTS");
        }

        Role customerRole = roleRepository.findByCode(Role.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Thiếu role " + Role.CUSTOMER + " trong bảng roles"));

        User user = new User();
        user.setEmail(email);
        user.setPhone(phone);
        user.setFullName(registerRequest.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(registerRequest.password()));
        user.addRole(customerRole);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            throw new AppException("Email hoặc số điện thoại đã được sử dụng", HttpStatus.CONFLICT,
                    "ACCOUNT_ALREADY_EXISTS");
        }
        return UserResponse.from(user);
    }

    @Transactional(noRollbackFor = AppException.class)
    public AuthSession login(LoginRequest loginRequest, ClientInfo clientInfo) {
        Instant now = clock.instant();
        String email = normalizeEmail(loginRequest.email());

        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty() || optionalUser.get().getStatus() == UserStatus.DELETED) {
            passwordEncoder.matches(loginRequest.password(), dummyPasswordHash);
            throw badCredentials();
        }
        User user = optionalUser.get();

        if (user.getStatus() == UserStatus.LOCKED && !user.isLoginAllowed(now)) {
            throw new AppException("Tài khoản tạm thời bị khóa do đăng nhập sai nhiều lần, vui lòng thử lại sau",
                    HttpStatus.LOCKED, "ACCOUNT_LOCKED");
        }

        if (!passwordMatches(loginRequest.password(), user)) {
            user.registerFailedLogin(MAX_FAILED_LOGIN_ATTEMPTS, LOCK_DURATION, now);
            throw badCredentials();
        }

        if (!user.isLoginAllowed(now)) {
            throw new AppException("Tài khoản đã bị tạm ngưng hoặc không khả dụng", HttpStatus.FORBIDDEN,
                    "ACCOUNT_DISABLED");
        }

        user.recordSuccessfulLogin(now);
        String rawRefreshToken = TokenHasher.generateRandomToken();
        saveRefreshToken(user, rawRefreshToken, UUID.randomUUID(), clientInfo, now);
        return buildSession(user, rawRefreshToken);
    }

    @Transactional
    public AuthSession openSession(User user, ClientInfo clientInfo) {
        String rawRefreshToken = TokenHasher.generateRandomToken();
        saveRefreshToken(user, rawRefreshToken, UUID.randomUUID(), clientInfo, clock.instant());
        return buildSession(user, rawRefreshToken);
    }

    @Transactional(noRollbackFor = AppException.class)
    public AuthSession refresh(String rawRefreshToken, ClientInfo clientInfo) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw invalidRefreshToken();
        }
        Instant now = clock.instant();

        RefreshToken currentRefreshToken = refreshTokenRepository
                .findByTokenHashForUpdate(TokenHasher.sha256Hex(rawRefreshToken))
                .orElseThrow(AuthService::invalidRefreshToken);

        if (currentRefreshToken.isRevoked()) {
            if (isWithinReuseGracePeriod(currentRefreshToken, now)) {
                throw new AppException("Phiên đăng nhập vừa được gia hạn bởi một yêu cầu khác",
                        HttpStatus.UNAUTHORIZED, REFRESH_IN_PROGRESS_ERROR_CODE);
            }
            refreshTokenRepository.revokeFamily(currentRefreshToken.getFamilyId(), now);
            throw new AppException("Phiên đăng nhập không còn hợp lệ, vui lòng đăng nhập lại",
                    HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED");
        }
        if (!currentRefreshToken.isActive(now)) {
            throw invalidRefreshToken();
        }

        User user = userRepository.findWithRolesById(currentRefreshToken.getUser().getId())
                .orElseThrow(AuthService::invalidRefreshToken);
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.DELETED) {
            refreshTokenRepository.revokeFamily(currentRefreshToken.getFamilyId(), now);
            throw new AppException("Tài khoản đã bị tạm ngưng hoặc không khả dụng", HttpStatus.UNAUTHORIZED,
                    "ACCOUNT_DISABLED");
        }

        String rawNextRefreshToken = TokenHasher.generateRandomToken();
        RefreshToken nextRefreshToken =
                saveRefreshToken(user, rawNextRefreshToken, currentRefreshToken.getFamilyId(), clientInfo, now);
        currentRefreshToken.revoke(now);
        currentRefreshToken.setReplacedByRefreshToken(nextRefreshToken);
        return buildSession(user, rawNextRefreshToken);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawRefreshToken))
                .ifPresent(refreshToken -> refreshTokenRepository.revokeFamily(refreshToken.getFamilyId(), now));
    }

    @Transactional
    public void logoutAll(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId, clock.instant());
    }

    private RefreshToken saveRefreshToken(
            User user, String rawRefreshToken, UUID familyId, ClientInfo clientInfo, Instant now) {
        ClientInfo safeClientInfo = clientInfo == null ? ClientInfo.unknown() : clientInfo;

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(TokenHasher.sha256Hex(rawRefreshToken));
        refreshToken.setFamilyId(familyId);
        refreshToken.setDeviceInfo(truncate(safeClientInfo.deviceInfo(), DEVICE_INFO_MAX_LENGTH));
        refreshToken.setIpAddress(truncate(safeClientInfo.ipAddress(), IP_ADDRESS_MAX_LENGTH));
        refreshToken.setExpiresAt(now.plus(refreshTokenTtl));
        return refreshTokenRepository.save(refreshToken);
    }

    private AuthSession buildSession(User user, String rawRefreshToken) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        return new AuthSession(
                UserResponse.from(user), accessToken, jwtTokenProvider.getAccessTokenTtl(), rawRefreshToken,
                refreshTokenTtl);
    }

    private boolean passwordMatches(String rawPassword, User user) {
        if (!user.hasPassword()) {
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            return false;
        }
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    private boolean isWithinReuseGracePeriod(RefreshToken refreshToken, Instant now) {
        return refreshToken.getReplacedByRefreshToken() != null
                && refreshToken.getRevokedAt() != null
                && now.isBefore(refreshToken.getRevokedAt().plus(REUSE_GRACE_PERIOD));
    }

    static void ensurePasswordFitsBcrypt(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            throw new AppException("Mật khẩu quá dài (tối đa 72 byte)", HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static AppException badCredentials() {
        return new AppException("Email hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS");
    }

    private static AppException invalidRefreshToken() {
        return new AppException("Phiên đăng nhập không hợp lệ hoặc đã hết hạn", HttpStatus.UNAUTHORIZED,
                "INVALID_REFRESH_TOKEN");
    }
}
