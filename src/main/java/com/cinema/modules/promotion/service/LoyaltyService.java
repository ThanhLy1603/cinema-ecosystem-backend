package com.cinema.modules.promotion.service;

import com.cinema.modules.auth.service.interfaces.IUserLoyaltyService;
import com.cinema.modules.promotion.entity.LoyaltyPointTransaction;
import com.cinema.modules.promotion.repository.LoyaltyPointTransactionRepository;
import com.cinema.modules.promotion.service.interfaces.ILoyaltyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;

@Service
public class LoyaltyService implements ILoyaltyService {

    static final String EARN_TYPE = "EARN";
    static final BigDecimal EARN_RATE = new BigDecimal("0.05");
    static final BigDecimal VND_PER_POINT = BigDecimal.TEN;

    private final LoyaltyPointTransactionRepository loyaltyPointTransactionRepository;
    private final IUserLoyaltyService userLoyaltyService;
    private final Clock clock;

    public LoyaltyService(
            LoyaltyPointTransactionRepository loyaltyPointTransactionRepository,
            IUserLoyaltyService userLoyaltyService,
            Clock clock) {
        this.loyaltyPointTransactionRepository = loyaltyPointTransactionRepository;
        this.userLoyaltyService = userLoyaltyService;
        this.clock = clock;
    }

    @Transactional
    @Override
    public int earnPointsForBooking(Long userId, Long bookingId, BigDecimal paidAmount) {
        if (loyaltyPointTransactionRepository.existsByBookingIdAndTransactionType(bookingId, EARN_TYPE)) {
            return 0;
        }
        int earnedPoints = paidAmount.multiply(EARN_RATE)
                .divide(VND_PER_POINT, 0, RoundingMode.DOWN)
                .intValue();
        if (earnedPoints <= 0) {
            return 0;
        }
        int balanceAfter = userLoyaltyService.addLoyaltyPoints(userId, earnedPoints);
        LoyaltyPointTransaction loyaltyPointTransaction = new LoyaltyPointTransaction();
        loyaltyPointTransaction.setUserId(userId);
        loyaltyPointTransaction.setBookingId(bookingId);
        loyaltyPointTransaction.setPointsChange(earnedPoints);
        loyaltyPointTransaction.setTransactionType(EARN_TYPE);
        loyaltyPointTransaction.setBalanceAfter(balanceAfter);
        loyaltyPointTransaction.setDescription("Tích điểm 5% giá trị thanh toán đơn vé");
        loyaltyPointTransaction.setCreatedAt(Instant.now(clock));
        loyaltyPointTransactionRepository.save(loyaltyPointTransaction);
        return earnedPoints;
    }
}
