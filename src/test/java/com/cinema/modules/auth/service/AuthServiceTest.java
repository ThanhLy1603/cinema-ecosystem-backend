package com.cinema.modules.auth.service;

import com.cinema.common.exception.AppException;
import com.cinema.config.JwtProperties;
import com.cinema.modules.auth.dto.request.LoginRequest;
import com.cinema.modules.auth.dto.request.RegisterRequest;
import com.cinema.modules.auth.dto.response.UserResponse;
import com.cinema.modules.auth.entity.RefreshToken;
import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.enums.MembershipTier;
import com.cinema.modules.auth.enums.UserStatus;
import com.cinema.modules.auth.model.AuthSession;
import com.cinema.modules.auth.model.ClientInfo;
import com.cinema.modules.auth.repository.RefreshTokenRepository;
import com.cinema.modules.auth.repository.RoleRepository;
import com.cinema.modules.auth.repository.UserRepository;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.auth.security.JwtTokenProvider;
import com.cinema.modules.auth.security.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String PASSWORD = "Matkhau123";
    private static final Instant START = Instant.parse("2026-11-06T10:00:00Z");
    private static final Duration REFRESH_TTL = Duration.ofSeconds(604800);

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zoneId) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private MutableClock clock;
    private JwtTokenProvider jwtTokenProvider;
    private AuthService authService;
    private Role customerRole;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        JwtProperties jwtProperties = new JwtProperties(SECRET, 900, REFRESH_TTL.toSeconds());
        jwtTokenProvider = new JwtTokenProvider(jwtProperties, clock);
        authService = new AuthService(
                userRepository, roleRepository, refreshTokenRepository, passwordEncoder, jwtTokenProvider,
                jwtProperties, clock);

        customerRole = new Role();
        customerRole.setCode(Role.CUSTOMER);
        lenient().when(roleRepository.findByCode(Role.CUSTOMER)).thenReturn(Optional.of(customerRole));
        lenient().when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    private User existingUser(String email, String password) {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setFullName("Nguyễn An");
        user.setPasswordHash(password == null ? null : passwordEncoder.encode(password));
        user.addRole(customerRole);
        lenient().when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        lenient().when(userRepository.findWithRolesById(1L)).thenReturn(Optional.of(user));
        return user;
    }

    private RefreshToken storedRefreshToken(User user, String rawToken, UUID familyId) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(TokenHasher.sha256Hex(rawToken));
        refreshToken.setFamilyId(familyId);
        refreshToken.setExpiresAt(clock.instant().plus(REFRESH_TTL));
        lenient().when(refreshTokenRepository.findByTokenHashForUpdate(refreshToken.getTokenHash()))
                .thenReturn(Optional.of(refreshToken));
        return refreshToken;
    }

    private AppException assertAppException(Runnable runnable, HttpStatus expectedStatus, String expectedErrorCode) {
        AppException appException = assertThrows(AppException.class, runnable::run);
        assertEquals(expectedStatus, appException.getStatus());
        assertEquals(expectedErrorCode, appException.getErrorCode());
        return appException;
    }

    private LoginRequest loginRequest(String email, String password) {
        return new LoginRequest(email, password);
    }

    @Test
    void registerCreatesCustomerWithHashedPasswordAndNormalizedEmail() {
        UserResponse userResponse = authService.register(
                new RegisterRequest("  An.Nguyen@Example.COM ", PASSWORD, "  Nguyễn An ", " 0901234567 "));

        ArgumentCaptor<User> userArgumentCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userArgumentCaptor.capture());
        User savedUser = userArgumentCaptor.getValue();

        assertEquals("an.nguyen@example.com", savedUser.getEmail());
        assertEquals("Nguyễn An", savedUser.getFullName());
        assertEquals("0901234567", savedUser.getPhone());
        assertNotEquals(PASSWORD, savedUser.getPasswordHash());
        assertTrue(passwordEncoder.matches(PASSWORD, savedUser.getPasswordHash()));
        assertTrue(savedUser.hasRole(Role.CUSTOMER));
        assertEquals(MembershipTier.STAR, savedUser.getTier());
        assertFalse(savedUser.isEmailVerified());
        assertEquals(java.util.List.of(Role.CUSTOMER), userResponse.roles());
        assertEquals("STAR", userResponse.tier());
    }

    @Test
    void registerStoresNullWhenPhoneIsBlank() {
        authService.register(new RegisterRequest("a@b.com", PASSWORD, "An", "   "));

        ArgumentCaptor<User> userArgumentCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userArgumentCaptor.capture());
        assertNull(userArgumentCaptor.getValue().getPhone());
    }

    @Test
    void registerRejectsDuplicateEmailAndPhone() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);
        assertAppException(() -> authService.register(new RegisterRequest("A@B.com", PASSWORD, "An", null)),
                HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS");

        when(userRepository.existsByEmail("c@d.com")).thenReturn(false);
        when(userRepository.existsByPhone("0901234567")).thenReturn(true);
        assertAppException(() -> authService.register(new RegisterRequest("c@d.com", PASSWORD, "An", "0901234567")),
                HttpStatus.CONFLICT, "PHONE_ALREADY_EXISTS");

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void registerTurnsDatabaseUniqueViolationRaceInto409() {
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertAppException(() -> authService.register(new RegisterRequest("a@b.com", PASSWORD, "An", null)),
                HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS");
    }

    @Test
    void registerRejectsPasswordLongerThan72BytesEvenIfShortInCharacters() {
        String longByBytes = "é".repeat(40) + "a1";
        assertAppException(() -> authService.register(new RegisterRequest("a@b.com", longByBytes, "An", null)),
                HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG");
    }

    @Test
    void loginIssuesAccessTokenAndStoresOnlyTheHashOfTheRefreshToken() {
        User user = existingUser("an@cinema.vn", PASSWORD);

        AuthSession authSession = authService.login(
                loginRequest(" AN@cinema.vn ", PASSWORD), new ClientInfo("x".repeat(400), "203.0.113.9"));

        Optional<AuthenticatedUser> optionalAuthenticatedUser = jwtTokenProvider.parseAccessToken(authSession.accessToken());
        assertTrue(optionalAuthenticatedUser.isPresent());
        assertEquals(1L, optionalAuthenticatedUser.get().userId());
        assertEquals(java.util.List.of(Role.CUSTOMER), optionalAuthenticatedUser.get().roles());

        ArgumentCaptor<RefreshToken> refreshTokenArgumentCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenArgumentCaptor.capture());
        RefreshToken savedRefreshToken = refreshTokenArgumentCaptor.getValue();
        assertNotEquals(authSession.refreshToken(), savedRefreshToken.getTokenHash());
        assertEquals(TokenHasher.sha256Hex(authSession.refreshToken()), savedRefreshToken.getTokenHash());
        assertEquals(START.plus(REFRESH_TTL), savedRefreshToken.getExpiresAt());
        assertEquals(255, savedRefreshToken.getDeviceInfo().length());
        assertEquals("203.0.113.9", savedRefreshToken.getIpAddress());
        assertNotNull(savedRefreshToken.getFamilyId());
        assertEquals(START, user.getLastLoginAt());
        assertEquals(REFRESH_TTL, authSession.refreshTokenTtl());
        assertFalse(authSession.toString().contains(authSession.refreshToken()), "toString không được lộ token");
    }

    @Test
    void loginWithUnknownEmailAndWrongPasswordGiveTheSameError() {
        when(userRepository.findByEmail("nobody@cinema.vn")).thenReturn(Optional.empty());
        AppException unknownEmailAppException = assertAppException(
                () -> authService.login(loginRequest("nobody@cinema.vn", PASSWORD), null),
                HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS");

        existingUser("an@cinema.vn", PASSWORD);
        AppException wrongPasswordAppException = assertAppException(
                () -> authService.login(loginRequest("an@cinema.vn", "SaiMatKhau1"), null),
                HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS");

        assertEquals(unknownEmailAppException.getMessage(), wrongPasswordAppException.getMessage());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void accountWithoutPasswordCannotLoginWithPassword() {
        existingUser("google@cinema.vn", null);

        assertAppException(() -> authService.login(loginRequest("google@cinema.vn", PASSWORD), null),
                HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS");
    }

    @Test
    void fiveWrongPasswordsLockTheAccountForFifteenMinutes() {
        User user = existingUser("an@cinema.vn", PASSWORD);

        for (int i = 0; i < 5; i++) {
            assertAppException(() -> authService.login(loginRequest("an@cinema.vn", "SaiMatKhau1"), null),
                    HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS");
        }
        assertEquals(UserStatus.LOCKED, user.getStatus());
        assertEquals(START.plus(Duration.ofMinutes(15)), user.getLockedUntil());

        assertAppException(() -> authService.login(loginRequest("an@cinema.vn", PASSWORD), null),
                HttpStatus.LOCKED, "ACCOUNT_LOCKED");

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        AuthSession authSession = authService.login(loginRequest("an@cinema.vn", PASSWORD), null);

        assertNotNull(authSession.accessToken());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(0, user.getFailedLoginAttempts());
    }

    @Test
    void suspendedAccountOnlyRevealsItsStatusAfterTheCorrectPassword() {
        User user = existingUser("an@cinema.vn", PASSWORD);
        user.setStatus(UserStatus.SUSPENDED);

        assertAppException(() -> authService.login(loginRequest("an@cinema.vn", "SaiMatKhau1"), null),
                HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS");
        assertAppException(() -> authService.login(loginRequest("an@cinema.vn", PASSWORD), null),
                HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED");
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void deletedAccountLooksLikeAnUnknownEmail() {
        User user = existingUser("an@cinema.vn", PASSWORD);
        user.setStatus(UserStatus.DELETED);

        assertAppException(() -> authService.login(loginRequest("an@cinema.vn", PASSWORD), null),
                HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS");
    }

    @Test
    void loginAndRefreshMustNotRollBackWhenTheyThrowAppException() throws NoSuchMethodException {
        Transactional loginTransactional = AuthService.class
                .getMethod("login", LoginRequest.class, ClientInfo.class).getAnnotation(Transactional.class);
        Transactional refreshTransactional = AuthService.class
                .getMethod("refresh", String.class, ClientInfo.class).getAnnotation(Transactional.class);

        assertEquals(AppException.class, loginTransactional.noRollbackFor()[0]);
        assertEquals(AppException.class, refreshTransactional.noRollbackFor()[0]);
    }

    @Test
    void refreshRotatesTokenWithinTheSameFamily() {
        User user = existingUser("an@cinema.vn", PASSWORD);
        UUID familyId = UUID.randomUUID();
        RefreshToken currentRefreshToken = storedRefreshToken(user, "raw-current", familyId);
        clock.advance(Duration.ofMinutes(5));

        AuthSession authSession = authService.refresh("raw-current", ClientInfo.unknown());

        assertTrue(currentRefreshToken.isRevoked());
        assertEquals(clock.instant(), currentRefreshToken.getRevokedAt());
        RefreshToken nextRefreshToken = currentRefreshToken.getReplacedByRefreshToken();
        assertNotNull(nextRefreshToken);
        assertEquals(familyId, nextRefreshToken.getFamilyId());
        assertEquals(TokenHasher.sha256Hex(authSession.refreshToken()), nextRefreshToken.getTokenHash());
        assertNotEquals("raw-current", authSession.refreshToken());
        assertEquals(clock.instant().plus(REFRESH_TTL), nextRefreshToken.getExpiresAt());
        assertTrue(jwtTokenProvider.parseAccessToken(authSession.accessToken()).isPresent());
        verify(refreshTokenRepository, never()).revokeFamily(any(UUID.class), any(Instant.class));
    }

    @Test
    void refreshRejectsMissingUnknownAndExpiredTokens() {
        assertAppException(() -> authService.refresh(null, null), HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
        assertAppException(() -> authService.refresh("  ", null), HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");

        when(refreshTokenRepository.findByTokenHashForUpdate(TokenHasher.sha256Hex("nope")))
                .thenReturn(Optional.empty());
        assertAppException(() -> authService.refresh("nope", null), HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");

        User user = existingUser("an@cinema.vn", PASSWORD);
        storedRefreshToken(user, "raw-old", UUID.randomUUID());
        clock.advance(REFRESH_TTL.plusSeconds(1));
        assertAppException(() -> authService.refresh("raw-old", null), HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");

        verify(refreshTokenRepository, never()).revokeFamily(any(UUID.class), any(Instant.class));
    }

    @Test
    void reusingARevokedTokenRevokesTheWholeFamily() {
        User user = existingUser("an@cinema.vn", PASSWORD);
        UUID familyId = UUID.randomUUID();
        RefreshToken stolenRefreshToken = storedRefreshToken(user, "raw-stolen", familyId);
        stolenRefreshToken.revoke(clock.instant());
        stolenRefreshToken.setReplacedByRefreshToken(new RefreshToken());
        clock.advance(Duration.ofMinutes(1));

        assertAppException(() -> authService.refresh("raw-stolen", null),
                HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED");

        verify(refreshTokenRepository).revokeFamily(eq(familyId), any(Instant.class));
    }

    @Test
    void revokedTokenReplayedWithinGracePeriodIsRejectedWithoutKillingTheSession() {
        User user = existingUser("an@cinema.vn", PASSWORD);
        RefreshToken justRotatedRefreshToken = storedRefreshToken(user, "raw-rotated", UUID.randomUUID());
        justRotatedRefreshToken.revoke(clock.instant());
        justRotatedRefreshToken.setReplacedByRefreshToken(new RefreshToken());
        clock.advance(AuthService.REUSE_GRACE_PERIOD.minusSeconds(1));

        assertAppException(() -> authService.refresh("raw-rotated", null),
                HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");

        verify(refreshTokenRepository, never()).revokeFamily(any(UUID.class), any(Instant.class));
    }

    @Test
    void refreshForSuspendedUserRevokesTheFamily() {
        User user = existingUser("an@cinema.vn", PASSWORD);
        UUID familyId = UUID.randomUUID();
        storedRefreshToken(user, "raw-current", familyId);
        user.setStatus(UserStatus.SUSPENDED);

        assertAppException(() -> authService.refresh("raw-current", null),
                HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED");

        verify(refreshTokenRepository).revokeFamily(eq(familyId), any(Instant.class));
    }

    @Test
    void temporarilyLockedUserCanStillRefreshSoAttackersCannotForceLogout() {
        User user = existingUser("an@cinema.vn", PASSWORD);
        storedRefreshToken(user, "raw-current", UUID.randomUUID());
        user.setStatus(UserStatus.LOCKED);
        user.setLockedUntil(clock.instant().plus(Duration.ofMinutes(10)));

        AuthSession authSession = authService.refresh("raw-current", null);

        assertNotNull(authSession.accessToken());
        verify(refreshTokenRepository, never()).revokeFamily(any(UUID.class), any(Instant.class));
    }

    @Test
    void logoutRevokesTheWholeFamilyOfTheCurrentSession() {
        User user = existingUser("an@cinema.vn", PASSWORD);
        UUID familyId = UUID.randomUUID();
        RefreshToken refreshToken = storedRefreshToken(user, "raw-current", familyId);
        when(refreshTokenRepository.findByTokenHash(refreshToken.getTokenHash()))
                .thenReturn(Optional.of(refreshToken));

        authService.logout("raw-current");

        verify(refreshTokenRepository).revokeFamily(familyId, clock.instant());
    }

    @Test
    void logoutIsIdempotentForUnknownOrMissingTokens() {
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex("unknown"))).thenReturn(Optional.empty());

        authService.logout("unknown");
        authService.logout(null);
        authService.logout("");

        verify(refreshTokenRepository, never()).revokeFamily(any(UUID.class), any(Instant.class));
    }

    @Test
    void logoutAllRevokesEveryTokenOfTheUser() {
        authService.logoutAll(1L);

        verify(refreshTokenRepository).revokeAllByUserId(1L, clock.instant());
    }
}
