package com.cinema.modules.showtime.service;

import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import com.cinema.modules.showtime.dto.response.ShowtimeConflictResponse;
import com.cinema.modules.showtime.entity.Showtime;
import com.cinema.modules.showtime.enums.ShowtimeConflictType;
import com.cinema.modules.showtime.repository.ShowtimeRepository;
import com.cinema.modules.showtime.util.ShowtimeRules;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ShowtimeConflictDetector {

    private static final String EXCLUSION_VIOLATION_SQL_STATE = "23P01";
    private static final Long NO_EXCLUDED_SHOWTIME_ID = -1L;

    private final ShowtimeRepository showtimeRepository;
    private final IMovieQueryService movieQueryService;

    public ShowtimeConflictDetector(ShowtimeRepository showtimeRepository, IMovieQueryService movieQueryService) {
        this.showtimeRepository = showtimeRepository;
        this.movieQueryService = movieQueryService;
    }

    public List<ShowtimeConflictResponse> findConflicts(
            Long hallId, Instant startTime, Instant endTime, Long excludedShowtimeId) {
        List<Showtime> conflictingShowtimes = showtimeRepository.findConflictingShowtimes(
                hallId, startTime, endTime, excludedShowtimeId == null ? NO_EXCLUDED_SHOWTIME_ID : excludedShowtimeId);
        if (conflictingShowtimes.isEmpty()) {
            return List.of();
        }
        Set<Long> movieIds = conflictingShowtimes.stream().map(Showtime::getMovieId).collect(Collectors.toSet());
        Map<Long, MovieInfoResponse> movieInfoResponses = movieQueryService.findMovieInfos(movieIds);
        return conflictingShowtimes.stream()
                .map(conflictingShowtime -> toConflictResponse(
                        conflictingShowtime, movieInfoResponses.get(conflictingShowtime.getMovieId()), startTime, endTime))
                .toList();
    }

    public boolean isOverlapViolation(Throwable throwable) {
        Throwable currentThrowable = throwable;
        while (currentThrowable != null) {
            if (currentThrowable instanceof SQLException sqlException
                    && EXCLUSION_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
            currentThrowable = currentThrowable.getCause() == currentThrowable ? null : currentThrowable.getCause();
        }
        return false;
    }

    static ShowtimeConflictType classify(Showtime conflictingShowtime, Instant startTime, Instant endTime) {
        boolean overlapsPlayback = startTime.isBefore(conflictingShowtime.getEndTime())
                && conflictingShowtime.getStartTime().isBefore(endTime);
        return overlapsPlayback ? ShowtimeConflictType.OVERLAP : ShowtimeConflictType.CLEANING_TIME;
    }

    private ShowtimeConflictResponse toConflictResponse(
            Showtime conflictingShowtime, MovieInfoResponse movieInfoResponse, Instant startTime, Instant endTime) {
        return new ShowtimeConflictResponse(
                conflictingShowtime.getId(),
                conflictingShowtime.getMovieId(),
                movieInfoResponse == null ? null : movieInfoResponse.title(),
                conflictingShowtime.getStartTime(),
                conflictingShowtime.getEndTime(),
                ShowtimeRules.calculateOccupiedUntil(conflictingShowtime.getEndTime()),
                classify(conflictingShowtime, startTime, endTime));
    }
}
