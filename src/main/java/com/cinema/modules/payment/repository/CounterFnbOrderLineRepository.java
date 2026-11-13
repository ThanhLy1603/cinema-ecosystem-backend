package com.cinema.modules.payment.repository;

import com.cinema.modules.payment.entity.CounterFnbOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CounterFnbOrderLineRepository extends JpaRepository<CounterFnbOrderLine, Long> {

    List<CounterFnbOrderLine> findByOrderIdOrderByIdAsc(Long orderId);
}
