package com.cinema.modules.booking.repository;

import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findFirstByUserIdAndStatus(Long userId, BookingStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.userId = :userId and booking.status = :status")
    Optional<Booking> findByUserIdAndStatusForUpdate(
            @Param("userId") Long userId, @Param("status") BookingStatus status);

    @Modifying
    @Query("update Booking booking set booking.status = :expiredStatus "
            + "where booking.status = :pendingStatus and booking.expiresAt < :now")
    int expireOverdueBookings(
            @Param("pendingStatus") BookingStatus pendingStatus,
            @Param("expiredStatus") BookingStatus expiredStatus,
            @Param("now") Instant now);
}
