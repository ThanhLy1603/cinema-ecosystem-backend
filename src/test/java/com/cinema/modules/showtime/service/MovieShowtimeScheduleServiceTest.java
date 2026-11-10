package com.cinema.modules.showtime.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.cinema.dto.response.CinemaOptionResponse;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import com.cinema.modules.showtime.dto.response.CinemaShowtimesResponse;
import com.cinema.modules.showtime.dto.response.MovieShowtimeDatesResponse;
import com.cinema.modules.showtime.entity.Showtime;
import com.cinema.modules.showtime.repository.ShowtimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieShowtimeScheduleServiceTest {

    private static final Long MOVIE_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");

    @Mock
    private ShowtimeRepository showtimeRepository;

    @Mock
    private ShowtimeSeatOccupancyReader showtimeSeatOccupancyReader;

    @Mock
    private ICinemaQueryService cinemaQueryService;

    @Mock
    private IMovieQueryService movieQueryService;

    private MovieShowtimeScheduleService movieShowtimeScheduleService;

    @BeforeEach
    void setUp() {
        movieShowtimeScheduleService = new MovieShowtimeScheduleService(
                showtimeRepository,
                showtimeSeatOccupancyReader,
                cinemaQueryService,
                movieQueryService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void unknownMovieIsRejectedWithNotFound() {
        when(movieQueryService.findMovieInfos(List.of(MOVIE_ID))).thenReturn(Map.of());

        AppException appException = assertThrows(AppException.class,
                () -> movieShowtimeScheduleService.findShowtimeDates(MOVIE_ID, YearMonth.of(2026, 11), null));

        assertEquals("MOVIE_NOT_FOUND", appException.getErrorCode());
        verify(showtimeRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void deletedMovieIsRejectedWithNotFound() {
        when(movieQueryService.findMovieInfos(List.of(MOVIE_ID))).thenReturn(Map.of(MOVIE_ID, movieInfo(true)));

        AppException appException = assertThrows(AppException.class,
                () -> movieShowtimeScheduleService.findShowtimesByDate(MOVIE_ID, LocalDate.of(2026, 11, 10), null));

        assertEquals("MOVIE_NOT_FOUND", appException.getErrorCode());
    }

    @Test
    void unknownCinemaIsRejectedWithNotFound() {
        when(movieQueryService.findMovieInfos(List.of(MOVIE_ID))).thenReturn(Map.of(MOVIE_ID, movieInfo(false)));
        when(cinemaQueryService.findActiveCinemaOptions()).thenReturn(List.of(cinemaOption(1L, "Rạp A")));

        AppException appException = assertThrows(AppException.class,
                () -> movieShowtimeScheduleService.findShowtimeDates(MOVIE_ID, YearMonth.of(2026, 11), 99L));

        assertEquals("CINEMA_NOT_FOUND", appException.getErrorCode());
    }

    @Test
    void datesAreDistinctSortedAndComputedInVietnamTime() {
        stubMovieAndSingleCinemaWithHall();
        Showtime lateNightShowtime = showtime(1L, Instant.parse("2026-11-12T17:30:00Z"));
        Showtime sameDayShowtime = showtime(2L, Instant.parse("2026-11-13T05:00:00Z"));
        Showtime earlierShowtime = showtime(3L, Instant.parse("2026-11-11T09:00:00Z"));
        when(showtimeRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(lateNightShowtime, sameDayShowtime, earlierShowtime));

        MovieShowtimeDatesResponse movieShowtimeDatesResponse =
                movieShowtimeScheduleService.findShowtimeDates(MOVIE_ID, YearMonth.of(2026, 11), null);

        assertEquals(
                List.of(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 13)),
                movieShowtimeDatesResponse.dates());
    }

    @Test
    void pastMonthReturnsNoDatesWithoutQueryingShowtimes() {
        stubMovieAndSingleCinemaWithHall();

        MovieShowtimeDatesResponse movieShowtimeDatesResponse =
                movieShowtimeScheduleService.findShowtimeDates(MOVIE_ID, YearMonth.of(2026, 9), null);

        assertTrue(movieShowtimeDatesResponse.dates().isEmpty());
        verify(showtimeRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void showtimesAreGroupedByCinemaWithSeatAvailability() {
        when(movieQueryService.findMovieInfos(List.of(MOVIE_ID))).thenReturn(Map.of(MOVIE_ID, movieInfo(false)));
        when(cinemaQueryService.findActiveCinemaOptions())
                .thenReturn(List.of(cinemaOption(1L, "Rạp A"), cinemaOption(2L, "Rạp B"), cinemaOption(3L, "Rạp C")));
        when(cinemaQueryService.findHallIdsByCinemaIds(Set.of(1L, 2L, 3L))).thenReturn(Set.of(10L, 20L));
        when(cinemaQueryService.findHallInfos(Set.of(10L, 20L))).thenReturn(Map.of(
                10L, hallInfo(10L, 1L, HallStatus.ACTIVE, false),
                20L, hallInfo(20L, 2L, HallStatus.ACTIVE, false)));
        Showtime availableShowtime = showtime(100L, Instant.parse("2026-11-10T11:00:00Z"), 10L);
        Showtime soldOutShowtime = showtime(200L, Instant.parse("2026-11-10T12:00:00Z"), 20L);
        when(showtimeRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(availableShowtime, soldOutShowtime));
        when(cinemaQueryService.countBookableSeatsByHall(Set.of(10L, 20L))).thenReturn(Map.of(10L, 100, 20L, 50));
        when(showtimeSeatOccupancyReader.countOccupiedSeatsByShowtime(anyCollection(), any(Instant.class)))
                .thenReturn(Map.of(100L, 30, 200L, 50));

        List<CinemaShowtimesResponse> cinemaShowtimesResponses =
                movieShowtimeScheduleService.findShowtimesByDate(MOVIE_ID, LocalDate.of(2026, 11, 10), null);

        assertEquals(2, cinemaShowtimesResponses.size());
        assertEquals("Rạp A", cinemaShowtimesResponses.get(0).cinemaName());
        assertEquals(70, cinemaShowtimesResponses.get(0).showtimes().get(0).availableSeats());
        assertFalse(cinemaShowtimesResponses.get(0).showtimes().get(0).soldOut());
        assertEquals("Rạp B", cinemaShowtimesResponses.get(1).cinemaName());
        assertEquals(0, cinemaShowtimesResponses.get(1).showtimes().get(0).availableSeats());
        assertTrue(cinemaShowtimesResponses.get(1).showtimes().get(0).soldOut());
    }

    @Test
    void inactiveAndDeletedHallsAreExcluded() {
        when(movieQueryService.findMovieInfos(List.of(MOVIE_ID))).thenReturn(Map.of(MOVIE_ID, movieInfo(false)));
        when(cinemaQueryService.findActiveCinemaOptions()).thenReturn(List.of(cinemaOption(1L, "Rạp A")));
        when(cinemaQueryService.findHallIdsByCinemaIds(Set.of(1L))).thenReturn(Set.of(10L, 11L));
        when(cinemaQueryService.findHallInfos(Set.of(10L, 11L))).thenReturn(Map.of(
                10L, hallInfo(10L, 1L, HallStatus.MAINTENANCE, false),
                11L, hallInfo(11L, 1L, HallStatus.ACTIVE, true)));

        List<CinemaShowtimesResponse> cinemaShowtimesResponses =
                movieShowtimeScheduleService.findShowtimesByDate(MOVIE_ID, LocalDate.of(2026, 11, 10), null);

        assertTrue(cinemaShowtimesResponses.isEmpty());
        verify(showtimeRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    private void stubMovieAndSingleCinemaWithHall() {
        when(movieQueryService.findMovieInfos(List.of(MOVIE_ID))).thenReturn(Map.of(MOVIE_ID, movieInfo(false)));
        when(cinemaQueryService.findActiveCinemaOptions()).thenReturn(List.of(cinemaOption(1L, "Rạp A")));
        when(cinemaQueryService.findHallIdsByCinemaIds(Set.of(1L))).thenReturn(Set.of(10L));
        when(cinemaQueryService.findHallInfos(Set.of(10L)))
                .thenReturn(Map.of(10L, hallInfo(10L, 1L, HallStatus.ACTIVE, false)));
    }

    private MovieInfoResponse movieInfo(boolean deleted) {
        return new MovieInfoResponse(
                MOVIE_ID, "Phim", "phim", 120, AgeRating.C13, null, MovieStatus.NOW_SHOWING, deleted);
    }

    private CinemaOptionResponse cinemaOption(Long id, String name) {
        return new CinemaOptionResponse(id, name, "Hà Nội");
    }

    private HallInfoResponse hallInfo(Long id, Long cinemaId, HallStatus hallStatus, boolean deleted) {
        return new HallInfoResponse(id, cinemaId, "Phòng " + id, ScreenType.SCREEN_2D, hallStatus, deleted);
    }

    private Showtime showtime(Long id, Instant startTime) {
        return showtime(id, startTime, 10L);
    }

    private Showtime showtime(Long id, Instant startTime, Long hallId) {
        Showtime showtime = new Showtime();
        showtime.setId(id);
        showtime.setMovieId(MOVIE_ID);
        showtime.setHallId(hallId);
        showtime.setStartTime(startTime);
        showtime.setEndTime(startTime.plusSeconds(7200));
        showtime.setBasePrice(new BigDecimal("90000"));
        return showtime;
    }
}
