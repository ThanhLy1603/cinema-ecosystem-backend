package com.cinema.modules.booking.repository;

import com.cinema.modules.booking.entity.BookingRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingRefundRepository extends JpaRepository<BookingRefund, Long> {

    Optional<BookingRefund> findByBookingId(Long bookingId);
}
