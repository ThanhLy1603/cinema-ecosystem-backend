package com.cinema.modules.booking.repository;

import com.cinema.modules.booking.entity.SeatReservation;
import com.cinema.modules.booking.enums.SeatReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeatReservationRepository extends JpaRepository<SeatReservation, Long> {

    Optional<SeatReservation> findByShowtimeIdAndSeatId(Long showtimeId, Long seatId);

    List<SeatReservation> findByBookingIdOrderBySeatId(Long bookingId);

    List<SeatReservation> findByStatusAndExpiresAtBefore(SeatReservationStatus status, Instant now);

    @Modifying
    @Query("delete from SeatReservation seatReservation "
            + "where seatReservation.id in :seatReservationIds and seatReservation.status = :heldStatus")
    int deleteHeldByIds(
            @Param("seatReservationIds") Collection<Long> seatReservationIds,
            @Param("heldStatus") SeatReservationStatus heldStatus);

    @Query("select seatReservation.showtimeId, count(seatReservation) from SeatReservation seatReservation "
            + "where seatReservation.showtimeId in :showtimeIds and seatReservation.status = :heldStatus "
            + "and seatReservation.expiresAt > :now group by seatReservation.showtimeId")
    List<Object[]> countActiveHeldByShowtimeIds(
            @Param("showtimeIds") Collection<Long> showtimeIds,
            @Param("heldStatus") SeatReservationStatus heldStatus,
            @Param("now") Instant now);

    @Modifying
    @Query("delete from SeatReservation seatReservation where seatReservation.bookingId = :bookingId")
    int deleteByBookingId(@Param("bookingId") Long bookingId);

    @Modifying
    @Query("update SeatReservation seatReservation set seatReservation.status = :bookedStatus "
            + "where seatReservation.bookingId = :bookingId and seatReservation.status = :heldStatus")
    int markBookedByBookingId(
            @Param("bookingId") Long bookingId,
            @Param("heldStatus") SeatReservationStatus heldStatus,
            @Param("bookedStatus") SeatReservationStatus bookedStatus);
}
