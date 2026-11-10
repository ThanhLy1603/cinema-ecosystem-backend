package com.cinema.modules.media.dto.request;

import com.cinema.modules.media.enums.MediaPurpose;
import jakarta.validation.constraints.NotNull;

public record UploadSignatureRequest(
        @NotNull(message = "Vui lòng chọn loại tệp cần tải lên")
        MediaPurpose purpose) {
}
