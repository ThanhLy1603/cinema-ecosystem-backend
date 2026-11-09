package com.cinema.modules.cinema.repository;

import com.cinema.modules.cinema.entity.SeatType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatTypeRepository extends JpaRepository<SeatType, Long> {

    List<SeatType> findAllByOrderByIdAsc();
}
