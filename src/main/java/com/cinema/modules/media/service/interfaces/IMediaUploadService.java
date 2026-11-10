package com.cinema.modules.media.service.interfaces;

import com.cinema.modules.media.dto.response.UploadSignatureResponse;
import com.cinema.modules.media.enums.MediaPurpose;

import java.util.Collection;

public interface IMediaUploadService {

    UploadSignatureResponse createUploadSignature(MediaPurpose mediaPurpose, Collection<String> roles);
}
