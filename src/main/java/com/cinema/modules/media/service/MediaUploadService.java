package com.cinema.modules.media.service;

import com.cinema.common.exception.AppException;
import com.cinema.config.CloudinaryProperties;
import com.cinema.modules.media.dto.response.UploadSignatureResponse;
import com.cinema.modules.media.enums.MediaPurpose;
import com.cinema.modules.media.service.interfaces.IMediaUploadService;
import com.cinema.modules.media.util.CloudinarySigner;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;

@Service
public class MediaUploadService implements IMediaUploadService {

    private static final String UPLOAD_URL_TEMPLATE = "https://api.cloudinary.com/v1_1/%s/%s/upload";

    private final CloudinaryProperties cloudinaryProperties;
    private final Clock clock;

    public MediaUploadService(CloudinaryProperties cloudinaryProperties, Clock clock) {
        this.cloudinaryProperties = cloudinaryProperties;
        this.clock = clock;
    }

    @Override
    public UploadSignatureResponse createUploadSignature(MediaPurpose mediaPurpose, Collection<String> roles) {
        if (!mediaPurpose.isAllowedFor(roles)) {
            throw new AppException(
                    "Bạn không có quyền tải lên loại tệp này", HttpStatus.FORBIDDEN, "MEDIA_FORBIDDEN");
        }
        if (!cloudinaryProperties.isConfigured()) {
            throw new AppException(
                    "Chưa cấu hình Cloudinary, vui lòng nhập đường dẫn ảnh hoặc video thủ công",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MEDIA_NOT_CONFIGURED");
        }
        long timestamp = clock.instant().getEpochSecond();
        String allowedFormats = String.join(",", mediaPurpose.getAllowedFormats());
        String signature = CloudinarySigner.sign(
                Map.of("allowed_formats", allowedFormats, "folder", mediaPurpose.getFolder(), "timestamp", String.valueOf(timestamp)),
                cloudinaryProperties.apiSecret());
        return new UploadSignatureResponse(
                UPLOAD_URL_TEMPLATE.formatted(cloudinaryProperties.cloudName(), mediaPurpose.getResourceType()),
                cloudinaryProperties.apiKey(),
                timestamp,
                signature,
                mediaPurpose.getFolder(),
                mediaPurpose.getResourceType(),
                mediaPurpose.getAllowedFormats(),
                mediaPurpose.getMaxFileSizeBytes());
    }
}
