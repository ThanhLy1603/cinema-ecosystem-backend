package com.cinema.modules.showtime.repository;

import com.cinema.modules.showtime.entity.Showtime;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, Long>, JpaSpecificationExecutor<Showtime> {

    @Query(value = "select showtime.* from showtimes showtime "
            + "where showtime.hall_id = :hallId and showtime.status = 'ACTIVE' and showtime.id <> :excludedShowtimeId "
            + "and showtime_occupied_range(showtime.start_time, showtime.end_time) "
            + "&& showtime_occupied_range(cast(:startTime as timestamptz), cast(:endTime as timestamptz)) "
            + "order by showtime.start_time, showtime.id",
            nativeQuery = true)
    List<Showtime> findConflictingShowtimes(
            @Param("hallId") Long hallId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("excludedShowtimeId") Long excludedShowtimeId);

    @Query("select showtime from Showtime showtime where showtime.status = :status "
            + "and showtime.endTime > :endsAfter and showtime.startTime < :startsBefore "
            + "order by showtime.startTime asc, showtime.id asc")
    List<Showtime> findByStatusAndTimeRange(
            @Param("status") ShowtimeStatus status,
            @Param("endsAfter") Instant endsAfter,
            @Param("startsBefore") Instant startsBefore);
}
