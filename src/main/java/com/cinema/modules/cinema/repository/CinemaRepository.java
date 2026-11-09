package com.cinema.modules.cinema.repository;

import com.cinema.modules.cinema.entity.Cinema;
import com.cinema.modules.cinema.enums.CinemaStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CinemaRepository extends JpaRepository<Cinema, Long>, JpaSpecificationExecutor<Cinema> {

    @EntityGraph(attributePaths = "facilities")
    List<Cinema> findWithFacilitiesByIdIn(Collection<Long> cinemaIds);

    @EntityGraph(attributePaths = "facilities")
    Optional<Cinema> findWithFacilitiesByIdAndDeleted(Long cinemaId, boolean deleted);

    @EntityGraph(attributePaths = "facilities")
    Optional<Cinema> findWithFacilitiesBySlugAndStatusAndDeleted(String slug, CinemaStatus status, boolean deleted);

    Optional<Cinema> findByIdAndDeleted(Long cinemaId, boolean deleted);

    boolean existsBySlugAndDeletedFalse(String slug);

    @Query("""
            select distinct cinema.city from Cinema cinema
            where cinema.status = :status and cinema.deleted = false
            order by cinema.city
            """)
    List<String> findDistinctCitiesByStatus(@Param("status") CinemaStatus status);
}
