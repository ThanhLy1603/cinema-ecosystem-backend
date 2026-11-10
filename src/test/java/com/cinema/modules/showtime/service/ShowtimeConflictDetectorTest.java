package com.cinema.modules.showtime.service;

import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import com.cinema.modules.showtime.dto.response.ShowtimeConflictResponse;
import com.cinema.modules.showtime.entity.Showtime;
import com.cinema.modules.showtime.enums.ShowtimeConflictType;
import com.cinema.modules.showtime.repository.ShowtimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowtimeConflictDetectorTest {

    private static final Long HALL_ID = 3L;
    private static final Instant EXISTING_START = Instant.parse("2026-01-02T11:00:00Z");
    private static final Instant EXISTING_END = Instant.parse("2026-01-02T13:00:00Z");

    @Mock
    private ShowtimeRepository showtimeRepository;

    @Mock
    private IMovieQueryService movieQueryService;

    private ShowtimeConflictDetector showtimeConflictDetector;
    private Showtime existingShowtime;

    @BeforeEach
    void setUp() {
        showtimeConflictDetector = new ShowtimeConflictDetector(showtimeRepository, movieQueryService);
        existingShowtime = new Showtime();
        existingShowtime.setId(40L);
        existingShowtime.setMovieId(9L);
        existingShowtime.setHallId(HALL_ID);
        existingShowtime.setStartTime(EXISTING_START);
        existingShowtime.setEndTime(EXISTING_END);
    }

    @Test
    void findConflictsReturnsEmptyWithoutLoadingMoviesWhenSlotIsFree() {
        when(showtimeRepository.findConflictingShowtimes(eq(HALL_ID), eq(EXISTING_END), eq(Instant.parse("2026-01-02T15:00:00Z")), eq(-1L)))
                .thenReturn(List.of());

        List<ShowtimeConflictResponse> conflicts = showtimeConflictDetector.findConflicts(
                HALL_ID, EXISTING_END, Instant.parse("2026-01-02T15:00:00Z"), null);

        assertTrue(conflicts.isEmpty());
        verifyNoInteractions(movieQueryService);
    }

    @Test
    void findConflictsReportsPlaybackOverlapWithMovieTitleAndOccupiedUntil() {
        Instant newStart = Instant.parse("2026-01-02T12:30:00Z");
        Instant newEnd = Instant.parse("2026-01-02T14:30:00Z");
        when(showtimeRepository.findConflictingShowtimes(HALL_ID, newStart, newEnd, 55L)).thenReturn(List.of(existingShowtime));
        when(movieQueryService.findMovieInfos(Set.of(9L)))
                .thenReturn(Map.of(9L, movieInfo("Mai")));

        List<ShowtimeConflictResponse> conflicts = showtimeConflictDetector.findConflicts(HALL_ID, newStart, newEnd, 55L);

        assertEquals(1, conflicts.size());
        ShowtimeConflictResponse showtimeConflictResponse = conflicts.get(0);
        assertEquals(40L, showtimeConflictResponse.showtimeId());
        assertEquals("Mai", showtimeConflictResponse.movieTitle());
        assertEquals(ShowtimeConflictType.OVERLAP, showtimeConflictResponse.conflictType());
        assertEquals(Instant.parse("2026-01-02T13:15:00Z"), showtimeConflictResponse.occupiedUntil());
    }

    @Test
    void classifyTreatsStartInsideCleaningWindowAsCleaningTime() {
        ShowtimeConflictType afterExisting = ShowtimeConflictDetector.classify(
                existingShowtime, Instant.parse("2026-01-02T13:10:00Z"), Instant.parse("2026-01-02T15:00:00Z"));
        ShowtimeConflictType beforeExisting = ShowtimeConflictDetector.classify(
                existingShowtime, Instant.parse("2026-01-02T08:50:00Z"), Instant.parse("2026-01-02T10:50:00Z"));

        assertEquals(ShowtimeConflictType.CLEANING_TIME, afterExisting);
        assertEquals(ShowtimeConflictType.CLEANING_TIME, beforeExisting);
    }

    @Test
    void classifyTreatsTouchingPlaybackBoundaryAsCleaningTimeNotOverlap() {
        ShowtimeConflictType conflictType = ShowtimeConflictDetector.classify(
                existingShowtime, EXISTING_END, Instant.parse("2026-01-02T15:00:00Z"));

        assertEquals(ShowtimeConflictType.CLEANING_TIME, conflictType);
    }

    @Test
    void isOverlapViolationRecognisesExclusionViolationSqlState() {
        DataIntegrityViolationException exclusionViolation = new DataIntegrityViolationException(
                "conflict", new RuntimeException("wrapper", new SQLException("exclusion", "23P01")));
        DataIntegrityViolationException uniqueViolation = new DataIntegrityViolationException(
                "duplicate", new SQLException("unique", "23505"));

        assertTrue(showtimeConflictDetector.isOverlapViolation(exclusionViolation));
        assertFalse(showtimeConflictDetector.isOverlapViolation(uniqueViolation));
    }

    private MovieInfoResponse movieInfo(String title) {
        return new MovieInfoResponse(9L, title, "mai", 120, AgeRating.C16, null, MovieStatus.NOW_SHOWING, false);
    }
}
