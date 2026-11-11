package com.cinema.modules.auth.service;

import com.cinema.modules.auth.dto.response.UserContactResponse;
import com.cinema.modules.auth.repository.UserRepository;
import com.cinema.modules.auth.service.interfaces.IUserQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserQueryService implements IUserQueryService {

    private final UserRepository userRepository;

    public UserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<UserContactResponse> findContact(Long userId) {
        return userRepository.findById(userId)
                .map(user -> new UserContactResponse(user.getId(), user.getEmail(), user.getFullName()));
    }
}
