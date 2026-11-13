package com.cinema.modules.auth.service;

import com.cinema.modules.auth.dto.response.CustomerLookupResponse;
import com.cinema.modules.auth.dto.response.UserContactResponse;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.enums.UserStatus;
import com.cinema.modules.auth.repository.UserRepository;
import com.cinema.modules.auth.service.interfaces.IUserQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
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

    @Transactional(readOnly = true)
    @Override
    public Optional<CustomerLookupResponse> findActiveCustomerByKeyword(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isEmpty()) {
            return Optional.empty();
        }
        Optional<User> optionalUser = normalizedKeyword.contains("@")
                ? userRepository.findByEmail(normalizedKeyword.toLowerCase(Locale.ROOT))
                : userRepository.findByPhone(normalizedKeyword.replaceAll("[\\s.-]", ""));
        return optionalUser
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .map(user -> new CustomerLookupResponse(
                        user.getId(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getPhone(),
                        user.getTier(),
                        user.getLoyaltyPoints()));
    }
}
