package com.cinema.modules.cinema.repository;

import com.cinema.modules.cinema.entity.CinemaFacility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CinemaFacilityRepository extends JpaRepository<CinemaFacility, Long> {

    List<CinemaFacility> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long facilityId);
}
