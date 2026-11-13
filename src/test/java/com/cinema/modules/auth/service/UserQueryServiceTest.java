package com.cinema.modules.auth.service;

import com.cinema.modules.auth.dto.response.CustomerLookupResponse;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.enums.UserStatus;
import com.cinema.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserQueryService userQueryService;
    private User user;

    @BeforeEach
    void setUp() {
        userQueryService = new UserQueryService(userRepository);
        user = new User();
        user.setId(9L);
        user.setEmail("khach@mail.vn");
        user.setFullName("Nguyễn Khách");
        user.setPhone("0901234567");
        user.setLoyaltyPoints(120);
    }

    @Test
    void keywordWithAtSignLooksUpByLowercaseEmail() {
        when(userRepository.findByEmail("khach@mail.vn")).thenReturn(Optional.of(user));

        CustomerLookupResponse customerLookupResponse =
                userQueryService.findActiveCustomerByKeyword("  Khach@Mail.VN ").orElseThrow();

        assertEquals(9L, customerLookupResponse.id());
        assertEquals(120, customerLookupResponse.loyaltyPoints());
    }

    @Test
    void otherKeywordLooksUpByPhoneIgnoringSeparators() {
        when(userRepository.findByPhone("0901234567")).thenReturn(Optional.of(user));

        assertEquals("Nguyễn Khách",
                userQueryService.findActiveCustomerByKeyword("0901 234.567").orElseThrow().fullName());
    }

    @Test
    void lockedCustomerIsNotReturned() {
        user.setStatus(UserStatus.LOCKED);
        when(userRepository.findByPhone("0901234567")).thenReturn(Optional.of(user));

        assertTrue(userQueryService.findActiveCustomerByKeyword("0901234567").isEmpty());
    }

    @Test
    void blankKeywordDoesNotQueryTheDatabase() {
        assertTrue(userQueryService.findActiveCustomerByKeyword("   ").isEmpty());
        verifyNoInteractions(userRepository);
    }
}
