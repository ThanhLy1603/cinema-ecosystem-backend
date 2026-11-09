package com.cinema.modules.auth.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.dto.request.ChangePasswordRequest;
import com.cinema.modules.auth.dto.request.UpdateProfileRequest;
import com.cinema.modules.auth.dto.response.LoginResponse;
import com.cinema.modules.auth.dto.response.UserProfileResponse;
import com.cinema.modules.auth.model.AuthSession;
import com.cinema.modules.auth.model.ClientInfo;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.auth.security.SessionCookieWriter;
import com.cinema.modules.auth.service.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final SessionCookieWriter sessionCookieWriter;

    public UserProfileController(UserProfileService userProfileService, SessionCookieWriter sessionCookieWriter) {
        this.userProfileService = userProfileService;
        this.sessionCookieWriter = sessionCookieWriter;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(ApiResponse.success(userProfileService.getProfile(authenticatedUser.userId())));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UpdateProfileRequest updateProfileRequest) {
        UserProfileResponse userProfileResponse =
                userProfileService.updateProfile(authenticatedUser.userId(), updateProfileRequest);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hồ sơ thành công", userProfileResponse));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<LoginResponse>> changePassword(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody ChangePasswordRequest changePasswordRequest,
            HttpServletRequest httpServletRequest) {
        ClientInfo clientInfo = new ClientInfo(
                httpServletRequest.getHeader(HttpHeaders.USER_AGENT), httpServletRequest.getRemoteAddr());
        AuthSession authSession =
                userProfileService.changePassword(authenticatedUser.userId(), changePasswordRequest, clientInfo);
        return sessionCookieWriter.okWithSessionCookies(authSession)
                .body(ApiResponse.success("Đổi mật khẩu thành công", authSession.toLoginResponse()));
    }
}
