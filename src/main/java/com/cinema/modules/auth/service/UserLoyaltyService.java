package com.cinema.modules.auth.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.repository.UserRepository;
import com.cinema.modules.auth.service.interfaces.IUserLoyaltyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserLoyaltyService implements IUserLoyaltyService {

    private final UserRepository userRepository;

    public UserLoyaltyService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    @Override
    public int addLoyaltyPoints(Long userId, int points) {
        if (userRepository.addLoyaltyPoints(userId, points) == 0) {
            throw new AppException("Không tìm thấy người dùng", HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
        }
        return userRepository.findLoyaltyPointsById(userId).orElse(0);
    }
}
