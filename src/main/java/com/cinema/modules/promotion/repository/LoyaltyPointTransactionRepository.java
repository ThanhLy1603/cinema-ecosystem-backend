package com.cinema.modules.promotion.repository;

import com.cinema.modules.promotion.entity.LoyaltyPointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoyaltyPointTransactionRepository extends JpaRepository<LoyaltyPointTransaction, Long> {

    boolean existsByBookingIdAndTransactionType(Long bookingId, String transactionType);
}
