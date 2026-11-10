package com.cinema.modules.booking.repository;

import com.cinema.modules.booking.entity.SeatReservation;
import com.cinema.modules.booking.enums.SeatReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeatReservationRepository extends JpaRepository<SeatReservation, Long> {

    Optional<SeatReservation> findByShowtimeIdAndSeatId(Long showtimeId, Long seatId);

    List<SeatReservation> findByBookingIdOrderBySeatId(Long bookingId);

    List<SeatReservation> findByStatusAndExpiresAtBefore(SeatReservationStatus status, Instant now);

    @Modifying
    @Query("delete from SeatReservation seatReservation "
            + "where seatReservation.showtimeId = :showtimeId and seatReservation.seatId = :seatId "
            + "and seatReservation.status = :heldStatus and seatReservation.expiresAt < :now")
    int deleteExpiredHold(
            @Param("showtimeId") Long showtimeId,
            @Param("seatId") Long seatId,
            @Param("heldStatus") SeatReservationStatus heldStatus,
            @Param("now") Instant now);
}
