package com.cinema.modules.showtime.repository;

import com.cinema.modules.showtime.entity.PriceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PriceRuleRepository extends JpaRepository<PriceRule, Long> {

    List<PriceRule> findByDeletedOrderByRuleNameAsc(boolean deleted);

    List<PriceRule> findByDeletedFalse();

    boolean existsByRuleNameIgnoreCaseAndDeletedFalse(String ruleName);

    boolean existsByRuleNameIgnoreCaseAndDeletedFalseAndIdNot(String ruleName, Long priceRuleId);
}
