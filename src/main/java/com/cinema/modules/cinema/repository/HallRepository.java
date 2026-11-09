package com.cinema.modules.cinema.repository;

import com.cinema.modules.cinema.entity.Hall;
import com.cinema.modules.cinema.enums.HallStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface HallRepository extends JpaRepository<Hall, Long> {

    List<Hall> findByCinemaIdOrderByNameAsc(Long cinemaId);

    List<Hall> findByCinemaIdAndStatusOrderByNameAsc(Long cinemaId, HallStatus status);

    List<Hall> findByCinemaIdInOrderByNameAsc(Collection<Long> cinemaIds);

    boolean existsByCinemaIdAndNameIgnoreCase(Long cinemaId, String name);

    boolean existsByCinemaIdAndNameIgnoreCaseAndIdNot(Long cinemaId, String name, Long hallId);
}
