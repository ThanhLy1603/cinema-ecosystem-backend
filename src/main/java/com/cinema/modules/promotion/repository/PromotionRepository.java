package com.cinema.modules.promotion.repository;

import com.cinema.modules.promotion.entity.Promotion;
import com.cinema.modules.promotion.enums.PromotionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    @Query("select promotion from Promotion promotion "
            + "where promotion.status = :status and promotion.startDate <= :now and promotion.endDate >= :now "
            + "and (promotion.usageLimit is null or coalesce(promotion.usedCount, 0) < promotion.usageLimit) "
            + "order by promotion.endDate asc, promotion.id asc")
    List<Promotion> findRunningPromotions(@Param("status") PromotionStatus status, @Param("now") Instant now);
}
