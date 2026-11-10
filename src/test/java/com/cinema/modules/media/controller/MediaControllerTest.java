package com.cinema.modules.media.controller;

import com.cinema.common.exception.AppException;
import com.cinema.common.exception.GlobalExceptionHandler;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.media.dto.response.UploadSignatureResponse;
import com.cinema.modules.media.enums.MediaPurpose;
import com.cinema.modules.media.service.interfaces.IMediaUploadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

    private static final List<String> ROLES = List.of("ROLE_CUSTOMER");

    @Mock
    private IMediaUploadService mediaUploadService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MediaController(mediaUploadService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(9L, "u@cinema.vn", ROLES);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(authenticatedUser, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passesCallerRolesAndReturnsSignatureWithoutSecret() throws Exception {
        when(mediaUploadService.createUploadSignature(MediaPurpose.USER_AVATAR, ROLES)).thenReturn(
                new UploadSignatureResponse(
                        "https://api.cloudinary.com/v1_1/demo/image/upload", "key-1", 1700000000L, "abc",
                        "cinema/users", "image", List.of("jpg"), 3145728L));

        mockMvc.perform(post("/api/v1/media/upload-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"USER_AVATAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signature").value("abc"))
                .andExpect(jsonPath("$.data.folder").value("cinema/users"))
                .andExpect(jsonPath("$.data.apiSecret").doesNotExist());
    }

    @Test
    void rejectsUnknownPurpose() throws Exception {
        mockMvc.perform(post("/api/v1/media/upload-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"EXECUTABLE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsForbiddenPurpose() throws Exception {
        when(mediaUploadService.createUploadSignature(MediaPurpose.MOVIE_POSTER, ROLES)).thenThrow(
                new AppException("Bạn không có quyền", HttpStatus.FORBIDDEN, "MEDIA_FORBIDDEN"));

        mockMvc.perform(post("/api/v1/media/upload-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"MOVIE_POSTER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MEDIA_FORBIDDEN"));
    }

    @Test
    void reportsMissingConfigurationAsServiceUnavailable() throws Exception {
        when(mediaUploadService.createUploadSignature(MediaPurpose.USER_AVATAR, ROLES)).thenThrow(
                new AppException("Chưa cấu hình Cloudinary", HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_NOT_CONFIGURED"));

        mockMvc.perform(post("/api/v1/media/upload-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"USER_AVATAR\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("MEDIA_NOT_CONFIGURED"));
    }
}
