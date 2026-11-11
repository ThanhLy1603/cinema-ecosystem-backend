package com.cinema.modules.booking.repository;

import com.cinema.modules.booking.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByBookingIdOrderBySeatIdAsc(Long bookingId);

    List<Ticket> findByBookingIdInOrderBySeatIdAsc(Collection<Long> bookingIds);
}
