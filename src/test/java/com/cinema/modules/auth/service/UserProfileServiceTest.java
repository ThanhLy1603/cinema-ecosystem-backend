package com.cinema.modules.auth.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.dto.request.ChangePasswordRequest;
import com.cinema.modules.auth.dto.request.UpdateProfileRequest;
import com.cinema.modules.auth.dto.response.UserProfileResponse;
import com.cinema.modules.auth.dto.response.UserResponse;
import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.entity.UserIdentity;
import com.cinema.modules.auth.enums.AuthProvider;
import com.cinema.modules.auth.enums.Gender;
import com.cinema.modules.auth.enums.UserStatus;
import com.cinema.modules.auth.model.AuthSession;
import com.cinema.modules.auth.model.ClientInfo;
import com.cinema.modules.auth.repository.RefreshTokenRepository;
import com.cinema.modules.auth.repository.UserIdentityRepository;
import com.cinema.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");
    private static final Long USER_ID = 7L;
    private static final String CURRENT_PASSWORD = "Matkhau123";
    private static final ClientInfo CLIENT_INFO = new ClientInfo("JUnit", "127.0.0.1");

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuthService authService;

    @Mock
    private BranchAccessService branchAccessService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private UserProfileService userProfileService;
    private Role customerRole;
    private AuthSession authSession;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(
                userRepository, userIdentityRepository, refreshTokenRepository, passwordEncoder, authService,
                branchAccessService, Clock.fixed(NOW, ZoneOffset.UTC));
        customerRole = new Role();
        customerRole.setCode(Role.CUSTOMER);
        authSession = new AuthSession(
                new UserResponse(USER_ID, "an@cinema.vn", "An", null, null, List.of(Role.CUSTOMER), 0, "STAR", false),
                "access", Duration.ofMinutes(15), "refresh", Duration.ofDays(7));
        lenient().when(userIdentityRepository.findByUserId(USER_ID)).thenReturn(List.of());
        lenient().when(branchAccessService.getAssignedBranches(USER_ID)).thenReturn(List.of());
        lenient().when(authService.openSession(any(User.class), any(ClientInfo.class))).thenReturn(authSession);
        lenient().when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    private User existingUser(String rawPassword, UserStatus status) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("an@cinema.vn");
        user.setFullName("Nguyễn An");
        user.setPasswordHash(rawPassword == null ? null : passwordEncoder.encode(rawPassword));
        user.setStatus(status);
        user.addRole(customerRole);
        lenient().when(userRepository.findWithRolesById(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }

    private AppException assertRejected(Runnable runnable, HttpStatus expectedStatus, String expectedErrorCode) {
        AppException appException = assertThrows(AppException.class, runnable::run);
        assertEquals(expectedStatus, appException.getStatus());
        assertEquals(expectedErrorCode, appException.getErrorCode());
        return appException;
    }

    private UpdateProfileRequest updateRequest(String phone) {
        return new UpdateProfileRequest(
                "  Trần Bình  ", phone, LocalDate.of(1995, 5, 20), Gender.FEMALE, "https://cdn.example.com/a.png");
    }

    @Test
    void getProfileReturnsPasswordFlagAndLinkedProviders() {
        existingUser(null, UserStatus.ACTIVE);
        UserIdentity userIdentity = new UserIdentity();
        userIdentity.setProvider(AuthProvider.GOOGLE);
        when(userIdentityRepository.findByUserId(USER_ID)).thenReturn(List.of(userIdentity));

        UserProfileResponse userProfileResponse = userProfileService.getProfile(USER_ID);

        assertEquals("an@cinema.vn", userProfileResponse.email());
        assertFalse(userProfileResponse.hasPassword());
        assertEquals(List.of("GOOGLE"), userProfileResponse.linkedProviders());
        assertEquals(List.of(Role.CUSTOMER), userProfileResponse.roles());
    }

    @Test
    void getProfileRejectsSuspendedAndUnknownUsers() {
        existingUser(CURRENT_PASSWORD, UserStatus.SUSPENDED);
        assertRejected(() -> userProfileService.getProfile(USER_ID), HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED");

        when(userRepository.findWithRolesById(USER_ID)).thenReturn(Optional.empty());
        assertRejected(() -> userProfileService.getProfile(USER_ID), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    @Test
    void updateProfileTrimsNameAndStoresAllEditableFields() {
        User user = existingUser(CURRENT_PASSWORD, UserStatus.ACTIVE);
        when(userRepository.existsByPhoneAndIdNot("0912345678", USER_ID)).thenReturn(false);

        UserProfileResponse userProfileResponse = userProfileService.updateProfile(USER_ID, updateRequest(" 0912345678 "));

        assertEquals("Trần Bình", user.getFullName());
        assertEquals("0912345678", user.getPhone());
        assertEquals(LocalDate.of(1995, 5, 20), user.getDateOfBirth());
        assertEquals(Gender.FEMALE, user.getGender());
        assertEquals("https://cdn.example.com/a.png", user.getAvatarUrl());
        assertEquals("Trần Bình", userProfileResponse.fullName());
        assertEquals("FEMALE", userProfileResponse.gender());
        assertEquals("an@cinema.vn", user.getEmail());
    }

    @Test
    void updateProfileClearsOptionalFieldsWhenOmitted() {
        User user = existingUser(CURRENT_PASSWORD, UserStatus.ACTIVE);
        user.setPhone("0912345678");
        user.setAvatarUrl("https://cdn.example.com/a.png");

        userProfileService.updateProfile(USER_ID, new UpdateProfileRequest("An", " ", null, null, null));

        assertNull(user.getPhone());
        assertNull(user.getDateOfBirth());
        assertNull(user.getGender());
        assertNull(user.getAvatarUrl());
    }

    @Test
    void updateProfileRejectsPhoneUsedByAnotherUser() {
        existingUser(CURRENT_PASSWORD, UserStatus.ACTIVE);
        when(userRepository.existsByPhoneAndIdNot("0912345678", USER_ID)).thenReturn(true);

        assertRejected(() -> userProfileService.updateProfile(USER_ID, updateRequest("0912345678")),
                HttpStatus.CONFLICT, "PHONE_ALREADY_EXISTS");

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void updateProfileTurnsDatabaseUniqueViolationRaceInto409() {
        existingUser(CURRENT_PASSWORD, UserStatus.ACTIVE);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertRejected(() -> userProfileService.updateProfile(USER_ID, updateRequest("0912345678")),
                HttpStatus.CONFLICT, "PHONE_ALREADY_EXISTS");
    }

    @Test
    void changePasswordHashesNewPasswordRevokesEverySessionThenOpensAFreshOne() {
        User user = existingUser(CURRENT_PASSWORD, UserStatus.ACTIVE);
        user.setFailedLoginAttempts(3);

        AuthSession result = userProfileService.changePassword(
                USER_ID, new ChangePasswordRequest(CURRENT_PASSWORD, "Matkhaumoi456"), CLIENT_INFO);

        assertSame(authSession, result);
        assertTrue(passwordEncoder.matches("Matkhaumoi456", user.getPasswordHash()));
        assertEquals(NOW, user.getPasswordChangedAt());
        assertEquals(0, user.getFailedLoginAttempts());
        InOrder inOrder = inOrder(refreshTokenRepository, authService);
        inOrder.verify(refreshTokenRepository).revokeAllByUserId(USER_ID, NOW);
        inOrder.verify(authService).openSession(user, CLIENT_INFO);
    }

    @Test
    void changePasswordRequiresTheCurrentPasswordWhenAccountHasOne() {
        User user = existingUser(CURRENT_PASSWORD, UserStatus.ACTIVE);

        assertRejected(() -> userProfileService.changePassword(
                        USER_ID, new ChangePasswordRequest(null, "Matkhaumoi456"), CLIENT_INFO),
                HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_REQUIRED");

        assertTrue(passwordEncoder.matches(CURRENT_PASSWORD, user.getPasswordHash()));
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(Long.class), any(Instant.class));
    }

    @Test
    void wrongCurrentPasswordIs400NotUnauthorizedAndCountsTowardsLockout() {
        User user = existingUser(CURRENT_PASSWORD, UserStatus.ACTIVE);

        assertRejected(() -> userProfileService.changePassword(
                        USER_ID, new ChangePasswordRequest("Saimatkhau1", "Matkhaumoi456"), CLIENT_INFO),
                HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD");

        assertEquals(1, user.getFailedLoginAttempts());
        assertTrue(passwordEncoder.matches(CURRENT_PASSWORD, user.getPasswordHash()));
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(Long.class), any(Instant.class));
    }

    @Test
    void fiveWrongCurrentPasswordsLockTheAccountAndFurtherAttemptsGet423() {
        User user = existingUser(CURRENT_PASSWORD, UserStatus.ACTIVE);
        ChangePasswordRequest wrongRequest = new ChangePasswordRequest("Saimatkhau1", "Matkhaumoi456");

        for (int i = 0; i < AuthService.MAX_FAILED_LOGIN_ATTEMPTS; i++) {
            assertRejected(() -> userProfileService.changePassword(USER_ID, wrongRequest, CLIENT_INFO),
                    HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD");
        }

        assertEquals(UserStatus.LOCKED, user.getStatus());
        assertRejected(() -> userProfileService.changePassword(
                        USER_ID, new ChangePasswordRequest(CURRENT_PASSWORD, "Matkhaumoi456"), CLIENT_INFO),
                HttpStatus.LOCKED, "ACCOUNT_LOCKED");
    }

    @Test
    void newPasswordMustDifferFromTheCurrentOne() {
        existingUser(CURRENT_PASSWORD, UserStatus.ACTIVE);

        assertRejected(() -> userProfileService.changePassword(
                        USER_ID, new ChangePasswordRequest(CURRENT_PASSWORD, CURRENT_PASSWORD), CLIENT_INFO),
                HttpStatus.BAD_REQUEST, "NEW_PASSWORD_SAME_AS_CURRENT");
    }

    @Test
    void newPasswordLongerThan72BytesIsRejectedEvenIfShortInCharacters() {
        existingUser(CURRENT_PASSWORD, UserStatus.ACTIVE);
        String multiBytePassword = "é1".repeat(30);

        assertRejected(() -> userProfileService.changePassword(
                        USER_ID, new ChangePasswordRequest(CURRENT_PASSWORD, multiBytePassword), CLIENT_INFO),
                HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG");
    }

    @Test
    void googleOnlyAccountCanSetAPasswordWithoutCurrentPassword() {
        User user = existingUser(null, UserStatus.ACTIVE);

        AuthSession result = userProfileService.changePassword(
                USER_ID, new ChangePasswordRequest(null, "Matkhaumoi456"), CLIENT_INFO);

        assertNotNull(result);
        assertTrue(user.hasPassword());
        assertTrue(passwordEncoder.matches("Matkhaumoi456", user.getPasswordHash()));
    }
}
