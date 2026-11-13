package com.cinema.modules.payment.repository;

import com.cinema.modules.payment.entity.CounterFnbOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CounterFnbOrderRepository extends JpaRepository<CounterFnbOrder, Long> {

    Optional<CounterFnbOrder> findByOrderCode(String orderCode);
}
