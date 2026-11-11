package com.cinema.modules.auth.service.interfaces;

public interface IUserLoyaltyService {

    int addLoyaltyPoints(Long userId, int points);
}
