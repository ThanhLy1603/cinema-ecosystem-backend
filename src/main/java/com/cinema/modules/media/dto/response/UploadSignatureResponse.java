package com.cinema.modules.media.dto.response;

import java.util.List;

public record UploadSignatureResponse(
        String uploadUrl,
        String apiKey,
        long timestamp,
        String signature,
        String folder,
        String resourceType,
        List<String> allowedFormats,
        long maxFileSizeBytes) {
}
