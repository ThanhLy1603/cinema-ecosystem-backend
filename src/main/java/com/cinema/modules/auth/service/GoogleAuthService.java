package com.cinema.modules.auth.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.dto.request.GoogleLoginRequest;
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
import com.cinema.modules.auth.service.interfaces.IGoogleAuthService;
import com.cinema.modules.auth.service.interfaces.IAuthService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class GoogleAuthService implements IGoogleAuthService {

    private static final int EMAIL_MAX_LENGTH = 150;
    private static final int FULL_NAME_MAX_LENGTH = 150;
    private static final String SECURE_URL_PREFIX = "https://";

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final RoleRepository roleRepository;
    private final IAuthService authService;
    private final Clock clock;

    public GoogleAuthService(
            GoogleIdTokenVerifier googleIdTokenVerifier,
            UserRepository userRepository,
            UserIdentityRepository userIdentityRepository,
            RoleRepository roleRepository,
            IAuthService authService,
            Clock clock) {
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.roleRepository = roleRepository;
        this.authService = authService;
        this.clock = clock;
    }

    @Transactional
    @Override
    public AuthSession login(GoogleLoginRequest googleLoginRequest, ClientInfo clientInfo) {
        GoogleProfile googleProfile = googleIdTokenVerifier.verify(googleLoginRequest.idToken());
        String email = googleProfile.email().trim().toLowerCase(Locale.ROOT);
        if (email.length() > EMAIL_MAX_LENGTH) {
            throw new AppException("Email Google không hợp lệ", HttpStatus.BAD_REQUEST, "INVALID_GOOGLE_EMAIL");
        }
        Instant now = clock.instant();

        Optional<UserIdentity> optionalUserIdentity = userIdentityRepository
                .findByProviderAndProviderUserId(AuthProvider.GOOGLE, googleProfile.subject());

        User user;
        UserIdentity userIdentity;
        if (optionalUserIdentity.isPresent()) {
            userIdentity = optionalUserIdentity.get();
            user = userIdentity.getUser();
        } else {
            Optional<User> optionalUser = userRepository.findByEmail(email);
            if (optionalUser.isPresent()) {
                user = optionalUser.get();
                userIdentity = linkToExistingUser(user, googleProfile, email);
            } else {
                user = registerNewUser(googleProfile, email);
                userIdentity = user.getIdentities().iterator().next();
            }
        }

        ensureLoginAllowed(user, now);
        userIdentity.setLastLoginAt(now);
        user.recordSuccessfulLogin(now);
        return authService.openSession(user, clientInfo);
    }

    private UserIdentity linkToExistingUser(User user, GoogleProfile googleProfile, String email) {
        if (user.getStatus() == UserStatus.DELETED) {
            throw accountDisabled();
        }
        if (!user.isEmailVerified()) {
            throw new AppException(
                    "Email này đã đăng ký bằng mật khẩu nhưng chưa được xác thực. Hãy đăng nhập bằng mật khẩu, "
                            + "xác thực email rồi đăng nhập lại bằng Google",
                    HttpStatus.CONFLICT, "ACCOUNT_EXISTS_UNVERIFIED");
        }
        if (userIdentityRepository.existsByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE)) {
            throw new AppException("Tài khoản này đã liên kết với một tài khoản Google khác", HttpStatus.CONFLICT,
                    "GOOGLE_ACCOUNT_MISMATCH");
        }
        UserIdentity userIdentity = newUserIdentity(googleProfile, email);
        userIdentity.setUser(user);
        return userIdentityRepository.save(userIdentity);
    }

    private User registerNewUser(GoogleProfile googleProfile, String email) {
        Role customerRole = roleRepository.findByCode(Role.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Thiếu role " + Role.CUSTOMER + " trong bảng roles"));

        User user = new User();
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setFullName(fullNameOf(googleProfile, email));
        user.setAvatarUrl(avatarUrlOf(googleProfile));
        user.addRole(customerRole);
        user.addIdentity(newUserIdentity(googleProfile, email));

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            throw new AppException("Tài khoản đang được tạo, vui lòng thử lại", HttpStatus.CONFLICT,
                    "ACCOUNT_ALREADY_EXISTS");
        }
    }

    private UserIdentity newUserIdentity(GoogleProfile googleProfile, String email) {
        UserIdentity userIdentity = new UserIdentity();
        userIdentity.setProvider(AuthProvider.GOOGLE);
        userIdentity.setProviderUserId(googleProfile.subject());
        userIdentity.setProviderEmail(email);
        return userIdentity;
    }

    private void ensureLoginAllowed(User user, Instant now) {
        if (user.isLoginAllowed(now)) {
            return;
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AppException("Tài khoản tạm thời bị khóa, vui lòng thử lại sau", HttpStatus.LOCKED,
                    "ACCOUNT_LOCKED");
        }
        throw accountDisabled();
    }

    private String fullNameOf(GoogleProfile googleProfile, String email) {
        String fullName = googleProfile.fullName() == null ? "" : googleProfile.fullName().trim();
        if (fullName.isEmpty()) {
            fullName = email.substring(0, email.indexOf('@'));
        }
        return fullName.length() <= FULL_NAME_MAX_LENGTH ? fullName : fullName.substring(0, FULL_NAME_MAX_LENGTH);
    }

    private String avatarUrlOf(GoogleProfile googleProfile) {
        String avatarUrl = googleProfile.avatarUrl();
        return avatarUrl != null && avatarUrl.startsWith(SECURE_URL_PREFIX) ? avatarUrl : null;
    }

    private static AppException accountDisabled() {
        return new AppException("Tài khoản đã bị tạm ngưng hoặc không khả dụng", HttpStatus.FORBIDDEN,
                "ACCOUNT_DISABLED");
    }
}
