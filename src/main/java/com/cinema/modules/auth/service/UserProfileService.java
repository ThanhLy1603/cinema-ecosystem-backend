package com.cinema.modules.auth.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.dto.request.ChangePasswordRequest;
import com.cinema.modules.auth.dto.request.UpdateProfileRequest;
import com.cinema.modules.auth.dto.response.UserProfileResponse;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.entity.UserIdentity;
import com.cinema.modules.auth.enums.AuthProvider;
import com.cinema.modules.auth.enums.UserStatus;
import com.cinema.modules.auth.model.AuthSession;
import com.cinema.modules.auth.model.ClientInfo;
import com.cinema.modules.auth.repository.RefreshTokenRepository;
import com.cinema.modules.auth.repository.UserIdentityRepository;
import com.cinema.modules.auth.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final Clock clock;

    public UserProfileService(
            UserRepository userRepository,
            UserIdentityRepository userIdentityRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            AuthService authService,
            Clock clock) {
        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        return toProfileResponse(loadUsableUser(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest updateProfileRequest) {
        User user = loadUsableUser(userId);
        String phone = normalizePhone(updateProfileRequest.phone());
        if (phone != null && userRepository.existsByPhoneAndIdNot(phone, userId)) {
            throw phoneAlreadyUsed();
        }

        user.setFullName(updateProfileRequest.fullName().trim());
        user.setPhone(phone);
        user.setDateOfBirth(updateProfileRequest.dateOfBirth());
        user.setGender(updateProfileRequest.gender());
        user.setAvatarUrl(updateProfileRequest.avatarUrl());

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            throw phoneAlreadyUsed();
        }
        return toProfileResponse(user);
    }

    @Transactional(noRollbackFor = AppException.class)
    public AuthSession changePassword(
            Long userId, ChangePasswordRequest changePasswordRequest, ClientInfo clientInfo) {
        Instant now = clock.instant();
        User user = loadUsableUser(userId);
        if (user.getStatus() == UserStatus.LOCKED && !user.isLoginAllowed(now)) {
            throw new AppException("Tài khoản tạm thời bị khóa do nhập sai mật khẩu nhiều lần, vui lòng thử lại sau",
                    HttpStatus.LOCKED, "ACCOUNT_LOCKED");
        }

        String newPassword = changePasswordRequest.newPassword();
        AuthService.ensurePasswordFitsBcrypt(newPassword);

        if (user.hasPassword()) {
            verifyCurrentPassword(user, changePasswordRequest.currentPassword(), now);
            if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
                throw new AppException("Mật khẩu mới phải khác mật khẩu hiện tại", HttpStatus.BAD_REQUEST,
                        "NEW_PASSWORD_SAME_AS_CURRENT");
            }
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(now);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        refreshTokenRepository.revokeAllByUserId(userId, now);
        return authService.openSession(loadUsableUser(userId), clientInfo);
    }

    private void verifyCurrentPassword(User user, String currentPassword, Instant now) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new AppException("Vui lòng nhập mật khẩu hiện tại", HttpStatus.BAD_REQUEST,
                    "CURRENT_PASSWORD_REQUIRED");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            user.registerFailedLogin(AuthService.MAX_FAILED_LOGIN_ATTEMPTS, AuthService.LOCK_DURATION, now);
            throw new AppException("Mật khẩu hiện tại không chính xác", HttpStatus.BAD_REQUEST,
                    "INVALID_CURRENT_PASSWORD");
        }
    }

    private User loadUsableUser(Long userId) {
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new AppException("Phiên đăng nhập không hợp lệ hoặc đã hết hạn",
                        HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"));
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.DELETED) {
            throw new AppException("Tài khoản đã bị tạm ngưng hoặc không khả dụng", HttpStatus.FORBIDDEN,
                    "ACCOUNT_DISABLED");
        }
        return user;
    }

    private UserProfileResponse toProfileResponse(User user) {
        List<AuthProvider> authProviders = userIdentityRepository.findByUserId(user.getId()).stream()
                .map(UserIdentity::getProvider)
                .toList();
        return UserProfileResponse.from(user, authProviders);
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.trim();
    }

    private static AppException phoneAlreadyUsed() {
        return new AppException("Số điện thoại đã được sử dụng", HttpStatus.CONFLICT, "PHONE_ALREADY_EXISTS");
    }
}
