package com.cinema.modules.auth.controller;

import com.cinema.common.exception.AppException;
import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.dto.request.GoogleLoginRequest;
import com.cinema.modules.auth.dto.request.LoginRequest;
import com.cinema.modules.auth.dto.request.RegisterRequest;
import com.cinema.modules.auth.dto.response.LoginResponse;
import com.cinema.modules.auth.dto.response.RefreshTokenResponse;
import com.cinema.modules.auth.dto.response.UserResponse;
import com.cinema.modules.auth.model.AuthSession;
import com.cinema.modules.auth.model.ClientInfo;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.auth.security.RefreshTokenCookieFactory;
import com.cinema.modules.auth.security.SessionCookieWriter;
import com.cinema.modules.auth.service.AuthService;
import com.cinema.modules.auth.service.GoogleAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String USER_AGENT_HEADER = HttpHeaders.USER_AGENT;

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;
    private final SessionCookieWriter sessionCookieWriter;

    public AuthController(
            AuthService authService,
            GoogleAuthService googleAuthService,
            SessionCookieWriter sessionCookieWriter) {
        this.authService = authService;
        this.googleAuthService = googleAuthService;
        this.sessionCookieWriter = sessionCookieWriter;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest registerRequest) {
        UserResponse userResponse = authService.register(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký thành công", userResponse));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest httpServletRequest) {
        AuthSession authSession = authService.login(loginRequest, clientInfoOf(httpServletRequest));
        return sessionCookieWriter.okWithSessionCookies(authSession)
                .body(ApiResponse.success("Đăng nhập thành công", authSession.toLoginResponse()));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<LoginResponse>> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest googleLoginRequest,
            HttpServletRequest httpServletRequest) {
        AuthSession authSession = googleAuthService.login(googleLoginRequest, clientInfoOf(httpServletRequest));
        return sessionCookieWriter.okWithSessionCookies(authSession)
                .body(ApiResponse.success("Đăng nhập thành công", authSession.toLoginResponse()));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToken(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String rawRefreshToken,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {
        try {
            AuthSession authSession = authService.refresh(rawRefreshToken, clientInfoOf(httpServletRequest));
            return sessionCookieWriter.okWithSessionCookies(authSession)
                    .body(ApiResponse.success(authSession.toRefreshTokenResponse()));
        } catch (AppException appException) {
            if (!AuthService.REFRESH_IN_PROGRESS_ERROR_CODE.equals(appException.getErrorCode())) {
                sessionCookieWriter.addClearedCookies(httpServletResponse);
            }
            throw appException;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String rawRefreshToken) {
        authService.logout(rawRefreshToken);
        return clearedCookieResponse("Đăng xuất thành công");
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        authService.logoutAll(authenticatedUser.userId());
        return clearedCookieResponse("Đã đăng xuất khỏi tất cả thiết bị");
    }

    private ResponseEntity<ApiResponse<Void>> clearedCookieResponse(String message) {
        return sessionCookieWriter.okWithClearedCookies().body(ApiResponse.success(message, null));
    }

    private ClientInfo clientInfoOf(HttpServletRequest httpServletRequest) {
        return new ClientInfo(httpServletRequest.getHeader(USER_AGENT_HEADER), httpServletRequest.getRemoteAddr());
    }
}
