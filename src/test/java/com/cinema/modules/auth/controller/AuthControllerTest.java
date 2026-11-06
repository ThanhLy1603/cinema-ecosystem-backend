package com.cinema.modules.auth.controller;

import com.cinema.common.AppException;
import com.cinema.common.GlobalExceptionHandler;
import com.cinema.config.CookieProperties;
import com.cinema.modules.auth.dto.request.LoginRequest;
import com.cinema.modules.auth.dto.request.RegisterRequest;
import com.cinema.modules.auth.dto.response.UserResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.auth.security.RefreshTokenCookieFactory;
import com.cinema.modules.auth.service.AuthService;
import com.cinema.modules.auth.service.AuthSession;
import com.cinema.modules.auth.service.ClientInfo;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final UserResponse USER_RESPONSE = new UserResponse(
            1L, "an@cinema.vn", "Nguyễn An", null, null, List.of("ROLE_CUSTOMER"), 0, "STAR", false);
    private static final String LOGIN_BODY = "{\"email\":\"an@cinema.vn\",\"password\":\"Matkhau123\"}";

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthController authController = new AuthController(
                authService, new RefreshTokenCookieFactory(new CookieProperties(true)));
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private AuthSession authSession(String rawRefreshToken) {
        return new AuthSession(USER_RESPONSE, "access-jwt", rawRefreshToken, REFRESH_TTL);
    }

    @Test
    void registerReturns201WithUserAndNoSecrets() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(USER_RESPONSE);

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"an@cinema.vn","password":"Matkhau123","fullName":"Nguyễn An"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("an@cinema.vn"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_CUSTOMER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.phone").doesNotExist());
    }

    @Test
    void registerRejectsInvalidInputWith400AndFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"not-an-email","password":"short","fullName":" "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.data.password").exists())
                .andExpect(jsonPath("$.data.fullName").exists());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    void passwordWithoutDigitIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"an@cinema.vn","password":"chiCoChuCai","fullName":"An"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.password").exists());
    }

    @Test
    void malformedJsonReturns400NotServerError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void wrongHttpMethodReturns405() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void loginPutsRefreshTokenInHttpOnlyCookieAndOnlyAccessTokenInBody() throws Exception {
        when(authService.login(any(LoginRequest.class), any(ClientInfo.class))).thenReturn(authSession("raw-refresh"));

        String setCookie = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.USER_AGENT, "JUnit-Agent")
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.data.user.email").value("an@cinema.vn"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertEquals(true, setCookie.contains("refresh_token=raw-refresh"), setCookie);
        assertEquals(true, setCookie.contains("HttpOnly"), setCookie);
        assertEquals(true, setCookie.contains("Secure"), setCookie);
        assertEquals(true, setCookie.contains("SameSite=Strict"), setCookie);
        assertEquals(true, setCookie.contains("Path=/api/v1/auth"), setCookie);
        assertEquals(true, setCookie.contains("Max-Age=604800"), setCookie);

        ArgumentCaptor<ClientInfo> clientInfoArgumentCaptor = ArgumentCaptor.forClass(ClientInfo.class);
        verify(authService).login(any(LoginRequest.class), clientInfoArgumentCaptor.capture());
        assertEquals("JUnit-Agent", clientInfoArgumentCaptor.getValue().deviceInfo());
        assertEquals("127.0.0.1", clientInfoArgumentCaptor.getValue().ipAddress());
    }

    @Test
    void failedLoginReturnsErrorWithoutSettingCookie() throws Exception {
        when(authService.login(any(LoginRequest.class), any(ClientInfo.class)))
                .thenThrow(new AppException("Email hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("BAD_CREDENTIALS"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void refreshReadsCookieAndRotatesIt() throws Exception {
        when(authService.refresh(eq("raw-old"), any(ClientInfo.class))).thenReturn(authSession("raw-new"));

        String setCookie = mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(new Cookie("refresh_token", "raw-old")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.data.user").doesNotExist())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertEquals(true, setCookie.contains("refresh_token=raw-new"), setCookie);
        assertEquals(true, setCookie.contains("HttpOnly"), setCookie);
    }

    @Test
    void refreshFailureClearsTheCookieAndReturns401() throws Exception {
        when(authService.refresh(eq("raw-bad"), any(ClientInfo.class)))
                .thenThrow(new AppException("hết hạn", HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN"));

        String setCookie = mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(new Cookie("refresh_token", "raw-bad")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"))
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertEquals(true, setCookie.contains("refresh_token="), setCookie);
        assertEquals(true, setCookie.contains("Max-Age=0"), setCookie);
    }

    @Test
    void refreshWithoutCookiePassesNullToTheService() throws Exception {
        when(authService.refresh(eq(null), any(ClientInfo.class)))
                .thenThrow(new AppException("thiếu", HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN"));

        mockMvc.perform(post("/api/v1/auth/refresh-token")).andExpect(status().isUnauthorized());

        verify(authService).refresh(eq(null), any(ClientInfo.class));
    }

    @Test
    void logoutRevokesTheCookieTokenAndClearsTheCookie() throws Exception {
        String setCookie = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refresh_token", "raw-current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        verify(authService).logout("raw-current");
        assertEquals(true, setCookie.contains("Max-Age=0"), setCookie);
    }

    @Test
    void logoutWithoutCookieStillSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isOk());

        verify(authService).logout(null);
    }

    @Test
    void logoutAllUsesThePrincipalUserIdAndClearsTheCookie() throws Exception {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(9L, "an@cinema.vn", List.of("ROLE_CUSTOMER"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(authenticatedUser, null, List.of()));

        String setCookie = mockMvc.perform(post("/api/v1/auth/logout-all"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        verify(authService).logoutAll(9L);
        assertEquals(true, setCookie.contains("Max-Age=0"), setCookie);
    }
}
