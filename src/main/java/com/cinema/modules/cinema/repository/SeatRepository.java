package com.cinema.modules.cinema.repository;

import com.cinema.modules.cinema.entity.Seat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    @EntityGraph(attributePaths = "seatType")
    List<Seat> findAllByHallIdOrderBySeatRowAscSeatNumberAsc(Long hallId);

    long countByHallId(Long hallId);
}
