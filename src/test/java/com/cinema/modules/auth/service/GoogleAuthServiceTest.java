package com.cinema.modules.auth.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.dto.request.GoogleLoginRequest;
import com.cinema.modules.auth.dto.response.UserResponse;
import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.entity.UserIdentity;
import com.cinema.modules.auth.enums.AuthProvider;
import com.cinema.modules.auth.enums.UserStatus;
import com.cinema.modules.auth.model.AuthSession;
import com.cinema.modules.auth.model.ClientInfo;
import com.cinema.modules.auth.model.GoogleProfile;
import com.cinema.modules.auth.repository.RoleRepository;
import com.cinema.modules.auth.repository.UserIdentityRepository;
import com.cinema.modules.auth.repository.UserRepository;
import com.cinema.modules.auth.security.GoogleIdTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");
    private static final String SUBJECT = "google-sub-1";
    private static final String ID_TOKEN = "google-id-token";
    private static final ClientInfo CLIENT_INFO = new ClientInfo("JUnit", "127.0.0.1");

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AuthService authService;

    private GoogleAuthService googleAuthService;
    private Role customerRole;
    private AuthSession authSession;

    @BeforeEach
    void setUp() {
        googleAuthService = new GoogleAuthService(
                googleIdTokenVerifier, userRepository, userIdentityRepository, roleRepository, authService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        customerRole = new Role();
        customerRole.setCode(Role.CUSTOMER);
        authSession = new AuthSession(
                new UserResponse(1L, "an@gmail.com", "An", null, null, List.of(Role.CUSTOMER), 0, "STAR", true),
                "access", Duration.ofMinutes(15), "refresh", Duration.ofDays(7));
        lenient().when(roleRepository.findByCode(Role.CUSTOMER)).thenReturn(Optional.of(customerRole));
        lenient().when(authService.openSession(any(User.class), any(ClientInfo.class))).thenReturn(authSession);
        lenient().when(userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, SUBJECT))
                .thenReturn(Optional.empty());
        lenient().when(userRepository.findByEmail(any(String.class))).thenReturn(Optional.empty());
        lenient().when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        lenient().when(userIdentityRepository.save(any(UserIdentity.class)))
                .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    private void givenGoogleProfile(String email, String fullName, String avatarUrl) {
        when(googleIdTokenVerifier.verify(ID_TOKEN))
                .thenReturn(new GoogleProfile(SUBJECT, email, fullName, avatarUrl));
    }

    private User existingUser(String email, boolean emailVerified, UserStatus status) {
        User user = new User();
        user.setId(7L);
        user.setEmail(email);
        user.setFullName("Nguyễn An");
        user.setPasswordHash("hash");
        user.setEmailVerified(emailVerified);
        user.setStatus(status);
        user.addRole(customerRole);
        lenient().when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        return user;
    }

    private AppException assertLoginRejected(HttpStatus expectedStatus, String expectedErrorCode) {
        AppException appException = assertThrows(AppException.class,
                () -> googleAuthService.login(new GoogleLoginRequest(ID_TOKEN), CLIENT_INFO));
        assertEquals(expectedStatus, appException.getStatus());
        assertEquals(expectedErrorCode, appException.getErrorCode());
        return appException;
    }

    @Test
    void newEmailCreatesVerifiedCustomerWithoutPasswordAndLinksGoogle() {
        givenGoogleProfile("An@Gmail.com", "Nguyễn An", "https://lh3.googleusercontent.com/a/avatar");

        AuthSession result = googleAuthService.login(new GoogleLoginRequest(ID_TOKEN), CLIENT_INFO);

        assertSame(authSession, result);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("an@gmail.com", savedUser.getEmail());
        assertNull(savedUser.getPasswordHash());
        assertFalse(savedUser.hasPassword());
        assertTrue(savedUser.isEmailVerified());
        assertEquals("Nguyễn An", savedUser.getFullName());
        assertEquals("https://lh3.googleusercontent.com/a/avatar", savedUser.getAvatarUrl());
        assertTrue(savedUser.hasRole(Role.CUSTOMER));
        assertEquals(1, savedUser.getIdentities().size());
        UserIdentity userIdentity = savedUser.getIdentities().iterator().next();
        assertEquals(AuthProvider.GOOGLE, userIdentity.getProvider());
        assertEquals(SUBJECT, userIdentity.getProviderUserId());
        assertEquals(NOW, userIdentity.getLastLoginAt());
        assertEquals(NOW, savedUser.getLastLoginAt());
        verify(authService).openSession(savedUser, CLIENT_INFO);
    }

    @Test
    void newUserWithoutNameFallsBackToEmailLocalPartAndDropsInsecureAvatar() {
        givenGoogleProfile("an@gmail.com", " ", "http://insecure.example.com/a.png");

        googleAuthService.login(new GoogleLoginRequest(ID_TOKEN), CLIENT_INFO);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertEquals("an", userCaptor.getValue().getFullName());
        assertNull(userCaptor.getValue().getAvatarUrl());
    }

    @Test
    void knownGoogleIdentityLogsTheLinkedUserInEvenIfGoogleEmailChanged() {
        User user = existingUser("old@gmail.com", true, UserStatus.ACTIVE);
        UserIdentity userIdentity = new UserIdentity();
        userIdentity.setUser(user);
        when(userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, SUBJECT))
                .thenReturn(Optional.of(userIdentity));
        givenGoogleProfile("new@gmail.com", "An", null);

        googleAuthService.login(new GoogleLoginRequest(ID_TOKEN), CLIENT_INFO);

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(userIdentityRepository, never()).save(any(UserIdentity.class));
        assertEquals(NOW, userIdentity.getLastLoginAt());
        assertEquals(NOW, user.getLastLoginAt());
        verify(authService).openSession(user, CLIENT_INFO);
    }

    @Test
    void verifiedExistingAccountGetsGoogleLinked() {
        User user = existingUser("an@gmail.com", true, UserStatus.ACTIVE);
        givenGoogleProfile("an@gmail.com", "An", null);

        googleAuthService.login(new GoogleLoginRequest(ID_TOKEN), CLIENT_INFO);

        ArgumentCaptor<UserIdentity> identityCaptor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).save(identityCaptor.capture());
        assertSame(user, identityCaptor.getValue().getUser());
        assertEquals(SUBJECT, identityCaptor.getValue().getProviderUserId());
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(authService).openSession(user, CLIENT_INFO);
    }

    @Test
    void unverifiedExistingAccountIsNotLinkedToPreventTakeover() {
        existingUser("an@gmail.com", false, UserStatus.ACTIVE);
        givenGoogleProfile("an@gmail.com", "An", null);

        assertLoginRejected(HttpStatus.CONFLICT, "ACCOUNT_EXISTS_UNVERIFIED");

        verify(userIdentityRepository, never()).save(any(UserIdentity.class));
        verify(authService, never()).openSession(any(User.class), any(ClientInfo.class));
    }

    @Test
    void accountAlreadyLinkedToAnotherGoogleAccountIsRejected() {
        User user = existingUser("an@gmail.com", true, UserStatus.ACTIVE);
        when(userIdentityRepository.existsByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE)).thenReturn(true);
        givenGoogleProfile("an@gmail.com", "An", null);

        assertLoginRejected(HttpStatus.CONFLICT, "GOOGLE_ACCOUNT_MISMATCH");

        verify(userIdentityRepository, never()).save(any(UserIdentity.class));
    }

    @Test
    void suspendedAccountCannotLoginWithGoogle() {
        existingUser("an@gmail.com", true, UserStatus.SUSPENDED);
        givenGoogleProfile("an@gmail.com", "An", null);

        assertLoginRejected(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED");

        verify(authService, never()).openSession(any(User.class), any(ClientInfo.class));
    }

    @Test
    void deletedAccountCannotBeLinkedOrLoggedIn() {
        existingUser("an@gmail.com", true, UserStatus.DELETED);
        givenGoogleProfile("an@gmail.com", "An", null);

        assertLoginRejected(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED");

        verify(userIdentityRepository, never()).save(any(UserIdentity.class));
    }

    @Test
    void temporarilyLockedAccountIsRejectedWith423() {
        User user = existingUser("an@gmail.com", true, UserStatus.LOCKED);
        user.setLockedUntil(NOW.plus(Duration.ofMinutes(5)));
        UserIdentity userIdentity = new UserIdentity();
        userIdentity.setUser(user);
        when(userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, SUBJECT))
                .thenReturn(Optional.of(userIdentity));
        givenGoogleProfile("an@gmail.com", "An", null);

        assertLoginRejected(HttpStatus.LOCKED, "ACCOUNT_LOCKED");
    }

    @Test
    void concurrentFirstLoginUniqueViolationBecomesConflict() {
        givenGoogleProfile("an@gmail.com", "An", null);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertLoginRejected(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS");
    }

    @Test
    void verifierFailureIsPropagatedAndNothingIsWritten() {
        when(googleIdTokenVerifier.verify(ID_TOKEN)).thenThrow(
                new AppException("invalid", HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN"));

        assertLoginRejected(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN");

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(authService, never()).openSession(any(User.class), any(ClientInfo.class));
    }
}
