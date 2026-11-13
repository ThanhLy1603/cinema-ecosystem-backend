package com.cinema.modules.payment.entity;

import com.cinema.modules.payment.enums.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "counter_fnb_orders")
public class CounterFnbOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, length = 30, updatable = false)
    private String orderCode;

    @Column(name = "cinema_id", nullable = false, updatable = false)
    private Long cinemaId;

    @Column(name = "sold_by_staff_user_id", nullable = false, updatable = false)
    private Long soldByStaffUserId;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20, updatable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "cash_received", precision = 12, scale = 2, updatable = false)
    private BigDecimal cashReceived;

    @Column(name = "change_amount", precision = 12, scale = 2, updatable = false)
    private BigDecimal changeAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
