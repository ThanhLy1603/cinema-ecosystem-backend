package com.cinema.modules.auth.controller;

import com.cinema.common.ApiResponse;
import com.cinema.common.AppException;
import com.cinema.modules.auth.dto.request.LoginRequest;
import com.cinema.modules.auth.dto.request.RegisterRequest;
import com.cinema.modules.auth.dto.response.LoginResponse;
import com.cinema.modules.auth.dto.response.RefreshTokenResponse;
import com.cinema.modules.auth.dto.response.UserResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.auth.security.RefreshTokenCookieFactory;
import com.cinema.modules.auth.service.AuthService;
import com.cinema.modules.auth.service.AuthSession;
import com.cinema.modules.auth.service.ClientInfo;
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
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    public AuthController(AuthService authService, RefreshTokenCookieFactory refreshTokenCookieFactory) {
        this.authService = authService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
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
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieOf(authSession))
                .body(ApiResponse.success("Đăng nhập thành công", authSession.toLoginResponse()));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToken(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String rawRefreshToken,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {
        try {
            AuthSession authSession = authService.refresh(rawRefreshToken, clientInfoOf(httpServletRequest));
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookieOf(authSession))
                    .body(ApiResponse.success(authSession.toRefreshTokenResponse()));
        } catch (AppException appException) {
            httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString());
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
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
                .body(ApiResponse.success(message, null));
    }

    private String refreshCookieOf(AuthSession authSession) {
        return refreshTokenCookieFactory.create(authSession.refreshToken(), authSession.refreshTokenTtl()).toString();
    }

    private ClientInfo clientInfoOf(HttpServletRequest httpServletRequest) {
        return new ClientInfo(httpServletRequest.getHeader(USER_AGENT_HEADER), httpServletRequest.getRemoteAddr());
    }
}
