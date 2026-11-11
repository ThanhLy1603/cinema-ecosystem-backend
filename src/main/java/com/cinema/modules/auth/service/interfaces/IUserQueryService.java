package com.cinema.modules.auth.service.interfaces;

import com.cinema.modules.auth.dto.response.UserContactResponse;

import java.util.Optional;

public interface IUserQueryService {

    Optional<UserContactResponse> findContact(Long userId);
}
