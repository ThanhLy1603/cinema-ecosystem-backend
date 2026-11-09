package com.cinema.modules.cinema.repository;

import com.cinema.modules.cinema.entity.Hall;
import com.cinema.modules.cinema.enums.HallStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface HallRepository extends JpaRepository<Hall, Long> {

    Optional<Hall> findByIdAndDeleted(Long hallId, boolean deleted);

    List<Hall> findByCinemaIdAndDeletedOrderByNameAsc(Long cinemaId, boolean deleted);

    List<Hall> findByCinemaIdAndStatusAndDeletedFalseOrderByNameAsc(Long cinemaId, HallStatus status);

    List<Hall> findByCinemaIdInAndDeletedFalseOrderByNameAsc(Collection<Long> cinemaIds);

    boolean existsByCinemaIdAndNameIgnoreCaseAndDeletedFalse(Long cinemaId, String name);

    boolean existsByCinemaIdAndNameIgnoreCaseAndIdNotAndDeletedFalse(Long cinemaId, String name, Long hallId);
}
