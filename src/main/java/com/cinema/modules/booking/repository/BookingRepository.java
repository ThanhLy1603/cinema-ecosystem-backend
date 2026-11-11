package com.cinema.modules.booking.repository;

import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findFirstByUserIdAndStatus(Long userId, BookingStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.userId = :userId and booking.status = :status")
    Optional<Booking> findByUserIdAndStatusForUpdate(
            @Param("userId") Long userId, @Param("status") BookingStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :bookingId")
    Optional<Booking> findByIdForUpdate(@Param("bookingId") Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.status = :status and booking.expiresAt < :now "
            + "order by booking.id")
    List<Booking> findOverdueForUpdate(@Param("status") BookingStatus status, @Param("now") Instant now);

    Optional<Booking> findByBookingCode(String bookingCode);

    List<Booking> findByUserIdAndStatusInOrderByCreatedAtDesc(Long userId, Collection<BookingStatus> statuses);
}
