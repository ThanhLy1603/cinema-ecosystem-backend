package com.cinema.modules.auth.controller;

import com.cinema.common.exception.AppException;
import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.config.CookieProperties;
import com.cinema.modules.auth.dto.request.ChangePasswordRequest;
import com.cinema.modules.auth.dto.request.UpdateProfileRequest;
import com.cinema.modules.auth.dto.response.UserProfileResponse;
import com.cinema.modules.auth.dto.response.UserResponse;
import com.cinema.modules.auth.model.AuthSession;
import com.cinema.modules.auth.model.ClientInfo;
import com.cinema.modules.auth.security.AccessTokenCookieFactory;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.auth.security.RefreshTokenCookieFactory;
import com.cinema.modules.auth.security.SessionCookieWriter;
import com.cinema.modules.auth.service.interfaces.IUserProfileService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    private static final UserProfileResponse PROFILE = new UserProfileResponse(
            9L, "an@cinema.vn", "Nguyễn An", "0912345678", null, LocalDate.of(1995, 5, 20), "FEMALE",
            List.of("ROLE_CUSTOMER"), 120, "GOLD", true, true, List.of("GOOGLE"), List.of());

    @Mock
    private IUserProfileService userProfileService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserProfileController userProfileController = new UserProfileController(
                userProfileService,
                new SessionCookieWriter(
                        new RefreshTokenCookieFactory(new CookieProperties(true)),
                        new AccessTokenCookieFactory(new CookieProperties(true))));
        mockMvc = MockMvcBuilders.standaloneSetup(userProfileController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(9L, "an@cinema.vn", List.of("ROLE_CUSTOMER"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(authenticatedUser, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getProfileReturnsTheProfileOfThePrincipalWithoutSecrets() throws Exception {
        when(userProfileService.getProfile(9L)).thenReturn(PROFILE);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("an@cinema.vn"))
                .andExpect(jsonPath("$.data.hasPassword").value(true))
                .andExpect(jsonPath("$.data.linkedProviders[0]").value("GOOGLE"))
                .andExpect(jsonPath("$.data.dateOfBirth").value("1995-05-20"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void updateProfilePassesTheValidatedBodyAndThePrincipalId() throws Exception {
        when(userProfileService.updateProfile(eq(9L), any(UpdateProfileRequest.class))).thenReturn(PROFILE);

        mockMvc.perform(put("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Nguyễn An","phone":"0912345678","dateOfBirth":"1995-05-20",
                         "gender":"FEMALE","avatarUrl":"https://cdn.example.com/a.png"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật hồ sơ thành công"));

        ArgumentCaptor<UpdateProfileRequest> captor = ArgumentCaptor.forClass(UpdateProfileRequest.class);
        verify(userProfileService).updateProfile(eq(9L), captor.capture());
        assertEquals(LocalDate.of(1995, 5, 20), captor.getValue().dateOfBirth());
    }

    @Test
    void updateProfileRejectsInvalidFieldsWith400() throws Exception {
        mockMvc.perform(put("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":" ","phone":"abc","dateOfBirth":"2999-01-01","avatarUrl":"http://insecure.example.com/a.png"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.fullName").exists())
                .andExpect(jsonPath("$.data.phone").exists())
                .andExpect(jsonPath("$.data.dateOfBirth").exists())
                .andExpect(jsonPath("$.data.avatarUrl").exists());

        verify(userProfileService, never()).updateProfile(any(Long.class), any(UpdateProfileRequest.class));
    }

    @Test
    void changePasswordIssuesFreshCookiesAndKeepsTokensOutOfTheBody() throws Exception {
        AuthSession authSession = new AuthSession(
                new UserResponse(9L, "an@cinema.vn", "An", null, null, List.of("ROLE_CUSTOMER"), 0, "STAR", true),
                "new-access", Duration.ofMinutes(15), "new-refresh", Duration.ofDays(7));
        when(userProfileService.changePassword(eq(9L), any(ChangePasswordRequest.class), any(ClientInfo.class)))
                .thenReturn(authSession);

        MvcResult mvcResult = mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Matkhau123\",\"newPassword\":\"Matkhaumoi456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("an@cinema.vn"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andReturn();

        String setCookie = String.join(" | ", mvcResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE));
        assertTrue(setCookie.contains("refresh_token=new-refresh"), setCookie);
        assertTrue(setCookie.contains("access_token=new-access"), setCookie);
    }

    @Test
    void changePasswordRejectsWeakNewPasswordBeforeReachingTheService() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Matkhau123\",\"newPassword\":\"onlyletters\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.newPassword").exists());

        verify(userProfileService, never())
                .changePassword(any(Long.class), any(ChangePasswordRequest.class), any(ClientInfo.class));
    }

    @Test
    void wrongCurrentPasswordIsReportedAs400SoTheClientDoesNotTryToRefresh() throws Exception {
        when(userProfileService.changePassword(eq(9L), any(ChangePasswordRequest.class), any(ClientInfo.class)))
                .thenThrow(new AppException("sai", HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD"));

        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Saimatkhau1\",\"newPassword\":\"Matkhaumoi456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CURRENT_PASSWORD"));
    }
}
