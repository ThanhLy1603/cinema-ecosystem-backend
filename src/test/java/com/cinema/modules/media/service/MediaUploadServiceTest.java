package com.cinema.modules.media.service;

import com.cinema.common.exception.AppException;
import com.cinema.config.CloudinaryProperties;
import com.cinema.modules.media.dto.response.UploadSignatureResponse;
import com.cinema.modules.media.enums.MediaPurpose;
import com.cinema.modules.media.util.CloudinarySigner;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaUploadServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final List<String> ADMIN = List.of("ROLE_ADMIN");
    private static final List<String> MANAGER = List.of("ROLE_MANAGER");
    private static final List<String> CUSTOMER = List.of("ROLE_CUSTOMER");

    private final MediaUploadService mediaUploadService =
            new MediaUploadService(new CloudinaryProperties("demo", "key-1", "secret-1"), CLOCK);

    @Test
    void rejectsWhenCloudinaryIsNotConfigured() {
        MediaUploadService unconfiguredService = new MediaUploadService(new CloudinaryProperties("", "", ""), CLOCK);

        AppException appException = assertThrows(
                AppException.class, () -> unconfiguredService.createUploadSignature(MediaPurpose.MOVIE_POSTER, ADMIN));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, appException.getStatus());
        assertEquals("MEDIA_NOT_CONFIGURED", appException.getErrorCode());
    }

    @Test
    void signsFolderFormatsAndTimestampWithoutExposingSecret() {
        UploadSignatureResponse uploadSignatureResponse =
                mediaUploadService.createUploadSignature(MediaPurpose.MOVIE_POSTER, ADMIN);

        String expectedSignature = CloudinarySigner.sign(
                Map.of(
                        "allowed_formats", "jpg,jpeg,png,webp",
                        "folder", "cinema/movies/posters",
                        "timestamp", String.valueOf(NOW.getEpochSecond())),
                "secret-1");
        assertEquals(expectedSignature, uploadSignatureResponse.signature());
        assertEquals("https://api.cloudinary.com/v1_1/demo/image/upload", uploadSignatureResponse.uploadUrl());
        assertEquals("key-1", uploadSignatureResponse.apiKey());
        assertEquals(NOW.getEpochSecond(), uploadSignatureResponse.timestamp());
        assertEquals(5L * 1024 * 1024, uploadSignatureResponse.maxFileSizeBytes());
        assertFalse(uploadSignatureResponse.toString().contains("secret-1"));
    }

    @Test
    void trailerUsesVideoEndpointAndVideoFormats() {
        UploadSignatureResponse uploadSignatureResponse =
                mediaUploadService.createUploadSignature(MediaPurpose.MOVIE_TRAILER, MANAGER);

        assertEquals("https://api.cloudinary.com/v1_1/demo/video/upload", uploadSignatureResponse.uploadUrl());
        assertEquals("video", uploadSignatureResponse.resourceType());
        assertEquals("cinema/movies/trailers", uploadSignatureResponse.folder());
        assertEquals(List.of("mp4", "webm", "mov"), uploadSignatureResponse.allowedFormats());
    }

    @Test
    void managerCanUploadMovieMediaButNotFacilityIcons() {
        assertEquals("cinema/movies/banners",
                mediaUploadService.createUploadSignature(MediaPurpose.MOVIE_BANNER, MANAGER).folder());
        assertEquals("cinema/persons",
                mediaUploadService.createUploadSignature(MediaPurpose.PERSON_AVATAR, MANAGER).folder());

        AppException appException = assertThrows(
                AppException.class, () -> mediaUploadService.createUploadSignature(MediaPurpose.FACILITY_ICON, MANAGER));

        assertEquals(HttpStatus.FORBIDDEN, appException.getStatus());
        assertEquals("MEDIA_FORBIDDEN", appException.getErrorCode());
    }

    @Test
    void adminCanUploadFacilityIconsIncludingSvg() {
        UploadSignatureResponse uploadSignatureResponse =
                mediaUploadService.createUploadSignature(MediaPurpose.FACILITY_ICON, ADMIN);

        assertEquals("cinema/facilities", uploadSignatureResponse.folder());
        assertEquals(true, uploadSignatureResponse.allowedFormats().contains("svg"));
    }

    @Test
    void anyAuthenticatedUserCanUploadOwnAvatarButNothingElse() {
        assertEquals("cinema/users",
                mediaUploadService.createUploadSignature(MediaPurpose.USER_AVATAR, CUSTOMER).folder());

        for (MediaPurpose staffPurpose : List.of(
                MediaPurpose.MOVIE_POSTER, MediaPurpose.MOVIE_BANNER, MediaPurpose.MOVIE_TRAILER,
                MediaPurpose.PERSON_AVATAR, MediaPurpose.FACILITY_ICON)) {
            AppException appException = assertThrows(
                    AppException.class, () -> mediaUploadService.createUploadSignature(staffPurpose, CUSTOMER));
            assertEquals("MEDIA_FORBIDDEN", appException.getErrorCode());
        }
    }
}
