package com.cinema.modules.cinema.repository;

import com.cinema.modules.cinema.entity.CinemaFacility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CinemaFacilityRepository extends JpaRepository<CinemaFacility, Long> {

    List<CinemaFacility> findAllByDeletedOrderByNameAsc(boolean deleted);

    Optional<CinemaFacility> findByIdAndDeleted(Long facilityId, boolean deleted);

    List<CinemaFacility> findAllByIdInAndDeletedFalse(Collection<Long> facilityIds);

    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndIdNotAndDeletedFalse(String name, Long facilityId);
}
