package com.cinema.modules.booking.repository;

import com.cinema.modules.booking.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByBookingIdOrderBySeatIdAsc(Long bookingId);

    List<Ticket> findByBookingIdInOrderBySeatIdAsc(Collection<Long> bookingIds);

    @Query("select ticket.showtimeId, count(ticket), sum(case when ticket.checkedIn = true then 1 else 0 end) "
            + "from Ticket ticket where ticket.showtimeId in :showtimeIds group by ticket.showtimeId")
    List<Object[]> countByShowtimeIds(@Param("showtimeIds") Collection<Long> showtimeIds);

    @Modifying
    @Query("delete from Ticket ticket where ticket.bookingId = :bookingId")
    int deleteByBookingId(@Param("bookingId") Long bookingId);
}
