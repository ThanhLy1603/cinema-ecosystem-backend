package com.cinema.modules.showtime.specification;

import com.cinema.modules.showtime.entity.Showtime;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;

public final class ShowtimeSpecifications {

    private ShowtimeSpecifications() {
    }

    public static Specification<Showtime> inHalls(Collection<Long> hallIds) {
        return (showtime, criteriaQuery, criteriaBuilder) -> showtime.get("hallId").in(hallIds);
    }

    public static Specification<Showtime> withStatus(ShowtimeStatus showtimeStatus) {
        return (showtime, criteriaQuery, criteriaBuilder) ->
                showtimeStatus == null ? null : criteriaBuilder.equal(showtime.get("status"), showtimeStatus);
    }

    public static Specification<Showtime> withMovie(Long movieId) {
        return (showtime, criteriaQuery, criteriaBuilder) ->
                movieId == null ? null : criteriaBuilder.equal(showtime.get("movieId"), movieId);
    }

    public static Specification<Showtime> overlapping(Instant windowStart, Instant windowEnd) {
        return (showtime, criteriaQuery, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.lessThan(showtime.get("startTime"), windowEnd),
                criteriaBuilder.greaterThan(showtime.get("endTime"), windowStart));
    }

    public static Specification<Showtime> startingBetween(Instant windowStart, Instant windowEnd) {
        return (showtime, criteriaQuery, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.greaterThanOrEqualTo(showtime.get("startTime"), windowStart),
                criteriaBuilder.lessThan(showtime.get("startTime"), windowEnd));
    }
}
