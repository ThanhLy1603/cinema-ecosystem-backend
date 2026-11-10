package com.cinema.modules.media.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.media.dto.request.UploadSignatureRequest;
import com.cinema.modules.media.dto.response.UploadSignatureResponse;
import com.cinema.modules.media.service.interfaces.IMediaUploadService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final IMediaUploadService mediaUploadService;

    public MediaController(IMediaUploadService mediaUploadService) {
        this.mediaUploadService = mediaUploadService;
    }

    @PostMapping("/upload-signature")
    public ResponseEntity<ApiResponse<UploadSignatureResponse>> createUploadSignature(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UploadSignatureRequest uploadSignatureRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                mediaUploadService.createUploadSignature(uploadSignatureRequest.purpose(), authenticatedUser.roles())));
    }
}
