package com.cinema.modules.auth.service.interfaces;

import com.cinema.modules.auth.dto.request.ChangePasswordRequest;
import com.cinema.modules.auth.dto.request.UpdateProfileRequest;
import com.cinema.modules.auth.dto.response.UserProfileResponse;
import com.cinema.modules.auth.model.AuthSession;
import com.cinema.modules.auth.model.ClientInfo;

public interface IUserProfileService {

    UserProfileResponse getProfile(Long userId);

    UserProfileResponse updateProfile(Long userId, UpdateProfileRequest updateProfileRequest);

    AuthSession changePassword(Long userId, ChangePasswordRequest changePasswordRequest, ClientInfo clientInfo);
}
