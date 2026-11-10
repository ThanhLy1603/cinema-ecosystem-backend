package com.cinema.modules.showtime.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import com.cinema.modules.showtime.dto.request.ShowtimeRequest;
import com.cinema.modules.showtime.dto.response.ShowtimeConflictResponse;
import com.cinema.modules.showtime.dto.response.ShowtimeResponse;
import com.cinema.modules.showtime.entity.Showtime;
import com.cinema.modules.showtime.enums.ShowtimeConflictType;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import com.cinema.modules.showtime.exception.ShowtimeConflictException;
import com.cinema.modules.showtime.repository.ShowtimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowtimeAdminServiceTest {

    private static final Long USER_ID = 4L;
    private static final Long CINEMA_ID = 1L;
    private static final Long HALL_ID = 3L;
    private static final Long MOVIE_ID = 9L;
    private static final Long SHOWTIME_ID = 70L;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant START = Instant.parse("2026-01-02T11:00:00Z");
    private static final Instant END = Instant.parse("2026-01-02T12:45:00Z");

    @Mock
    private ShowtimeRepository showtimeRepository;

    @Mock
    private ShowtimeConflictDetector showtimeConflictDetector;

    @Mock
    private ShowtimeBookingChecker showtimeBookingChecker;

    @Mock
    private ShowtimeViewAssembler showtimeViewAssembler;

    @Mock
    private ICinemaQueryService cinemaQueryService;

    @Mock
    private IMovieQueryService movieQueryService;

    @Mock
    private IBranchAccessService branchAccessService;

    private ShowtimeAdminService showtimeAdminService;
    private ShowtimeResponse showtimeResponse;

    @BeforeEach
    void setUp() {
        showtimeAdminService = new ShowtimeAdminService(
                showtimeRepository,
                new ShowtimeSlotPlanner(cinemaQueryService, movieQueryService, branchAccessService, showtimeConflictDetector),
                new ShowtimePersister(showtimeRepository, showtimeConflictDetector),
                showtimeBookingChecker,
                showtimeViewAssembler,
                cinemaQueryService,
                movieQueryService,
                branchAccessService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        showtimeResponse = new ShowtimeResponse(
                SHOWTIME_ID, MOVIE_ID, "Mai", AgeRating.C16, 105, CINEMA_ID, HALL_ID, "Phòng 01", ScreenType.SCREEN_2D,
                START, END, END.plusSeconds(900), new BigDecimal("90000"), ShowtimeStatus.ACTIVE, false, true, true);
    }

    private void givenActiveCinemaWithHall(HallStatus hallStatus) {
        when(cinemaQueryService.findHallInfos(List.of(HALL_ID))).thenReturn(Map.of(
                HALL_ID, new HallInfoResponse(HALL_ID, CINEMA_ID, "Phòng 01", ScreenType.SCREEN_2D, hallStatus, false)));
        when(cinemaQueryService.findActiveCinemaIds(List.of(CINEMA_ID))).thenReturn(Set.of(CINEMA_ID));
    }

    private void givenBookableHallAndMovie(HallStatus hallStatus, MovieStatus movieStatus, int durationMinutes) {
        givenActiveCinemaWithHall(hallStatus);
        when(movieQueryService.findMovieInfos(List.of(MOVIE_ID))).thenReturn(Map.of(
                MOVIE_ID, new MovieInfoResponse(MOVIE_ID, "Mai", "mai", durationMinutes, AgeRating.C16, null, movieStatus, false)));
    }

    private ShowtimeRequest request(Instant startTime) {
        return new ShowtimeRequest(MOVIE_ID, HALL_ID, startTime, new BigDecimal("90000"));
    }

    private Showtime existingShowtime(ShowtimeStatus showtimeStatus, Instant startTime) {
        Showtime showtime = new Showtime();
        showtime.setId(SHOWTIME_ID);
        showtime.setMovieId(MOVIE_ID);
        showtime.setHallId(HALL_ID);
        showtime.setStartTime(startTime);
        showtime.setEndTime(startTime.plusSeconds(105 * 60));
        showtime.setBasePrice(new BigDecimal("90000"));
        showtime.setStatus(showtimeStatus);
        return showtime;
    }

    private void givenHallOfExistingShowtime() {
        when(cinemaQueryService.findHallInfos(List.of(HALL_ID))).thenReturn(Map.of(
                HALL_ID, new HallInfoResponse(HALL_ID, CINEMA_ID, "Phòng 01", ScreenType.SCREEN_2D, HallStatus.ACTIVE, false)));
    }

    @Test
    void createStoresEndTimeAsStartPlusDurationWithoutCleaningMinutes() {
        givenBookableHallAndMovie(HallStatus.ACTIVE, MovieStatus.NOW_SHOWING, 105);
        when(showtimeConflictDetector.findConflicts(HALL_ID, START, END, null)).thenReturn(List.of());
        when(showtimeRepository.saveAndFlush(any(Showtime.class))).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        when(showtimeViewAssembler.toShowtimeResponses(anyCollectionOfShowtime(), eq(NOW))).thenReturn(List.of(showtimeResponse));

        ShowtimeResponse created = showtimeAdminService.createShowtime(USER_ID, request(START));

        ArgumentCaptor<Showtime> showtimeCaptor = ArgumentCaptor.forClass(Showtime.class);
        verify(showtimeRepository).saveAndFlush(showtimeCaptor.capture());
        assertEquals(START, showtimeCaptor.getValue().getStartTime());
        assertEquals(END, showtimeCaptor.getValue().getEndTime());
        assertEquals(ShowtimeStatus.ACTIVE, showtimeCaptor.getValue().getStatus());
        assertSame(showtimeResponse, created);
        verify(branchAccessService).assertCanAccessCinema(USER_ID, CINEMA_ID);
    }

    @Test
    void createTruncatesSecondsFromStartTime() {
        givenBookableHallAndMovie(HallStatus.ACTIVE, MovieStatus.NOW_SHOWING, 105);
        when(showtimeConflictDetector.findConflicts(HALL_ID, START, END, null)).thenReturn(List.of());
        when(showtimeRepository.saveAndFlush(any(Showtime.class))).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        when(showtimeViewAssembler.toShowtimeResponses(anyCollectionOfShowtime(), eq(NOW))).thenReturn(List.of(showtimeResponse));

        showtimeAdminService.createShowtime(USER_ID, request(START.plusSeconds(37)));

        ArgumentCaptor<Showtime> showtimeCaptor = ArgumentCaptor.forClass(Showtime.class);
        verify(showtimeRepository).saveAndFlush(showtimeCaptor.capture());
        assertEquals(START, showtimeCaptor.getValue().getStartTime());
    }

    @Test
    void createRejectsOverlapWithConflictDetailsAndDoesNotSave() {
        givenBookableHallAndMovie(HallStatus.ACTIVE, MovieStatus.NOW_SHOWING, 105);
        ShowtimeConflictResponse showtimeConflictResponse = new ShowtimeConflictResponse(
                40L, 8L, "Đào, phở và piano", START.minusSeconds(3600), START.plusSeconds(600), START.plusSeconds(1500),
                ShowtimeConflictType.OVERLAP);
        when(showtimeConflictDetector.findConflicts(HALL_ID, START, END, null)).thenReturn(List.of(showtimeConflictResponse));

        ShowtimeConflictException showtimeConflictException = assertThrows(
                ShowtimeConflictException.class, () -> showtimeAdminService.createShowtime(USER_ID, request(START)));

        assertEquals("SHOWTIME_CONFLICT", showtimeConflictException.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, showtimeConflictException.getStatus());
        assertEquals(List.of(showtimeConflictResponse), showtimeConflictException.getConflicts());
        verify(showtimeRepository, never()).saveAndFlush(any(Showtime.class));
    }

    @Test
    void createMapsConcurrentExclusionViolationToConflictWithFreshDetails() {
        givenBookableHallAndMovie(HallStatus.ACTIVE, MovieStatus.NOW_SHOWING, 105);
        ShowtimeConflictResponse racedConflict = new ShowtimeConflictResponse(
                41L, 8L, "Mai", START, END, END.plusSeconds(900), ShowtimeConflictType.OVERLAP);
        when(showtimeConflictDetector.findConflicts(HALL_ID, START, END, null))
                .thenReturn(List.of())
                .thenReturn(List.of(racedConflict));
        DataIntegrityViolationException exclusionViolation =
                new DataIntegrityViolationException("conflict", new SQLException("exclusion", "23P01"));
        when(showtimeRepository.saveAndFlush(any(Showtime.class))).thenThrow(exclusionViolation);
        when(showtimeConflictDetector.isOverlapViolation(exclusionViolation)).thenReturn(true);

        ShowtimeConflictException showtimeConflictException = assertThrows(
                ShowtimeConflictException.class, () -> showtimeAdminService.createShowtime(USER_ID, request(START)));

        assertEquals(List.of(racedConflict), showtimeConflictException.getConflicts());
    }

    @Test
    void createRethrowsOtherIntegrityViolations() {
        givenBookableHallAndMovie(HallStatus.ACTIVE, MovieStatus.NOW_SHOWING, 105);
        when(showtimeConflictDetector.findConflicts(HALL_ID, START, END, null)).thenReturn(List.of());
        DataIntegrityViolationException otherViolation =
                new DataIntegrityViolationException("other", new SQLException("check", "23514"));
        when(showtimeRepository.saveAndFlush(any(Showtime.class))).thenThrow(otherViolation);
        when(showtimeConflictDetector.isOverlapViolation(otherViolation)).thenReturn(false);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class, () -> showtimeAdminService.createShowtime(USER_ID, request(START)));

        assertSame(otherViolation, thrown);
    }

    @Test
    void createRejectsStartTimeNotInFuture() {
        givenBookableHallAndMovie(HallStatus.ACTIVE, MovieStatus.NOW_SHOWING, 105);

        AppException appException = assertThrows(AppException.class, () -> showtimeAdminService.createShowtime(USER_ID, request(NOW)));

        assertEquals("SHOWTIME_START_IN_PAST", appException.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, appException.getStatus());
        verify(showtimeConflictDetector, never()).findConflicts(any(), any(), any(), any());
    }

    @Test
    void createRejectsHallUnderMaintenance() {
        givenActiveCinemaWithHall(HallStatus.MAINTENANCE);

        AppException appException = assertThrows(AppException.class, () -> showtimeAdminService.createShowtime(USER_ID, request(START)));

        assertEquals("HALL_NOT_ACTIVE", appException.getErrorCode());
    }

    @Test
    void createRejectsEndedMovie() {
        givenBookableHallAndMovie(HallStatus.ACTIVE, MovieStatus.ENDED, 105);

        AppException appException = assertThrows(AppException.class, () -> showtimeAdminService.createShowtime(USER_ID, request(START)));

        assertEquals("MOVIE_ENDED", appException.getErrorCode());
    }

    @Test
    void createRejectsUnknownHall() {
        when(cinemaQueryService.findHallInfos(List.of(HALL_ID))).thenReturn(Map.of());

        AppException appException = assertThrows(AppException.class, () -> showtimeAdminService.createShowtime(USER_ID, request(START)));

        assertEquals("HALL_NOT_FOUND", appException.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, appException.getStatus());
    }

    @Test
    void createRejectsInactiveCinema() {
        when(cinemaQueryService.findHallInfos(List.of(HALL_ID))).thenReturn(Map.of(
                HALL_ID, new HallInfoResponse(HALL_ID, CINEMA_ID, "Phòng 01", ScreenType.SCREEN_2D, HallStatus.ACTIVE, false)));
        when(cinemaQueryService.findActiveCinemaIds(List.of(CINEMA_ID))).thenReturn(Set.of());

        AppException appException = assertThrows(AppException.class, () -> showtimeAdminService.createShowtime(USER_ID, request(START)));

        assertEquals("CINEMA_NOT_ACTIVE", appException.getErrorCode());
    }

    @Test
    void createStopsWhenActorCannotAccessCinema() {
        when(cinemaQueryService.findHallInfos(List.of(HALL_ID))).thenReturn(Map.of(
                HALL_ID, new HallInfoResponse(HALL_ID, CINEMA_ID, "Phòng 01", ScreenType.SCREEN_2D, HallStatus.ACTIVE, false)));
        doThrow(new AppException("Không có quyền", HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED"))
                .when(branchAccessService).assertCanAccessCinema(USER_ID, CINEMA_ID);

        AppException appException = assertThrows(AppException.class, () -> showtimeAdminService.createShowtime(USER_ID, request(START)));

        assertEquals("BRANCH_ACCESS_DENIED", appException.getErrorCode());
        verify(showtimeRepository, never()).saveAndFlush(any(Showtime.class));
    }

    @Test
    void updateExcludesItselfFromConflictSearchAndKeepsShowtimeId() {
        Showtime showtime = existingShowtime(ShowtimeStatus.ACTIVE, START.plusSeconds(7200));
        when(showtimeRepository.findById(SHOWTIME_ID)).thenReturn(Optional.of(showtime));
        when(showtimeBookingChecker.findShowtimeIdsWithBookings(List.of(SHOWTIME_ID), NOW)).thenReturn(Set.of());
        givenBookableHallAndMovie(HallStatus.ACTIVE, MovieStatus.NOW_SHOWING, 105);
        when(showtimeConflictDetector.findConflicts(HALL_ID, START, END, SHOWTIME_ID)).thenReturn(List.of());
        when(showtimeRepository.saveAndFlush(any(Showtime.class))).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        when(showtimeViewAssembler.toShowtimeResponses(anyCollectionOfShowtime(), eq(NOW))).thenReturn(List.of(showtimeResponse));

        showtimeAdminService.updateShowtime(USER_ID, SHOWTIME_ID, request(START));

        assertEquals(START, showtime.getStartTime());
        assertEquals(END, showtime.getEndTime());
        assertEquals(SHOWTIME_ID, showtime.getId());
    }

    @Test
    void updateRejectsShowtimeThatAlreadyStarted() {
        Showtime showtime = existingShowtime(ShowtimeStatus.ACTIVE, NOW.minusSeconds(60));
        when(showtimeRepository.findById(SHOWTIME_ID)).thenReturn(Optional.of(showtime));
        givenHallOfExistingShowtime();

        AppException appException = assertThrows(
                AppException.class, () -> showtimeAdminService.updateShowtime(USER_ID, SHOWTIME_ID, request(START)));

        assertEquals("SHOWTIME_STARTED", appException.getErrorCode());
    }

    @Test
    void updateRejectsShowtimeWithBookings() {
        Showtime showtime = existingShowtime(ShowtimeStatus.ACTIVE, START);
        when(showtimeRepository.findById(SHOWTIME_ID)).thenReturn(Optional.of(showtime));
        givenHallOfExistingShowtime();
        when(showtimeBookingChecker.findShowtimeIdsWithBookings(List.of(SHOWTIME_ID), NOW)).thenReturn(Set.of(SHOWTIME_ID));

        AppException appException = assertThrows(
                AppException.class, () -> showtimeAdminService.updateShowtime(USER_ID, SHOWTIME_ID, request(START)));

        assertEquals("SHOWTIME_HAS_BOOKINGS", appException.getErrorCode());
        verify(showtimeRepository, never()).saveAndFlush(any(Showtime.class));
    }

    @Test
    void updateRejectsCancelledShowtime() {
        Showtime showtime = existingShowtime(ShowtimeStatus.CANCELLED, START);
        when(showtimeRepository.findById(SHOWTIME_ID)).thenReturn(Optional.of(showtime));
        givenHallOfExistingShowtime();

        AppException appException = assertThrows(
                AppException.class, () -> showtimeAdminService.updateShowtime(USER_ID, SHOWTIME_ID, request(START)));

        assertEquals("SHOWTIME_CANCELLED", appException.getErrorCode());
    }

    @Test
    void cancelMarksShowtimeCancelledSoItReleasesTheHall() {
        Showtime showtime = existingShowtime(ShowtimeStatus.ACTIVE, START);
        when(showtimeRepository.findById(SHOWTIME_ID)).thenReturn(Optional.of(showtime));
        givenHallOfExistingShowtime();
        when(showtimeBookingChecker.findShowtimeIdsWithBookings(List.of(SHOWTIME_ID), NOW)).thenReturn(Set.of());
        when(showtimeRepository.saveAndFlush(showtime)).thenReturn(showtime);
        when(showtimeViewAssembler.toShowtimeResponses(anyCollectionOfShowtime(), eq(NOW))).thenReturn(List.of(showtimeResponse));

        showtimeAdminService.cancelShowtime(USER_ID, SHOWTIME_ID);

        assertEquals(ShowtimeStatus.CANCELLED, showtime.getStatus());
    }

    @Test
    void cancelRejectsShowtimeWithBookings() {
        Showtime showtime = existingShowtime(ShowtimeStatus.ACTIVE, START);
        when(showtimeRepository.findById(SHOWTIME_ID)).thenReturn(Optional.of(showtime));
        givenHallOfExistingShowtime();
        when(showtimeBookingChecker.findShowtimeIdsWithBookings(List.of(SHOWTIME_ID), NOW)).thenReturn(Set.of(SHOWTIME_ID));

        AppException appException = assertThrows(AppException.class, () -> showtimeAdminService.cancelShowtime(USER_ID, SHOWTIME_ID));

        assertEquals("SHOWTIME_HAS_BOOKINGS", appException.getErrorCode());
        assertEquals(ShowtimeStatus.ACTIVE, showtime.getStatus());
    }

    @Test
    void cancelRejectsShowtimeThatAlreadyEnded() {
        Showtime showtime = existingShowtime(ShowtimeStatus.ACTIVE, NOW.minusSeconds(3 * 3600));
        when(showtimeRepository.findById(SHOWTIME_ID)).thenReturn(Optional.of(showtime));
        givenHallOfExistingShowtime();

        AppException appException = assertThrows(AppException.class, () -> showtimeAdminService.cancelShowtime(USER_ID, SHOWTIME_ID));

        assertEquals("SHOWTIME_ENDED", appException.getErrorCode());
    }

    @Test
    void getShowtimeReturnsNotFoundForUnknownId() {
        when(showtimeRepository.findById(SHOWTIME_ID)).thenReturn(Optional.empty());

        AppException appException = assertThrows(AppException.class, () -> showtimeAdminService.getShowtime(USER_ID, SHOWTIME_ID));

        assertEquals("SHOWTIME_NOT_FOUND", appException.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, appException.getStatus());
    }

    @Test
    void listShowtimesChecksBranchAccessBeforeQuerying() {
        doThrow(new AppException("Không có quyền", HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED"))
                .when(branchAccessService).assertCanAccessCinema(USER_ID, CINEMA_ID);

        assertThrows(AppException.class, () -> showtimeAdminService.listShowtimes(
                USER_ID, CINEMA_ID, LocalDate.of(2026, 1, 2), null, null));

        verify(showtimeRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void listShowtimesReturnsEmptyWhenCinemaHasNoHalls() {
        when(cinemaQueryService.findAllCinemaIds()).thenReturn(Set.of(CINEMA_ID));
        when(cinemaQueryService.findHallIdsByCinemaIds(List.of(CINEMA_ID))).thenReturn(Set.of());

        List<ShowtimeResponse> showtimeResponses = showtimeAdminService.listShowtimes(
                USER_ID, CINEMA_ID, LocalDate.of(2026, 1, 2), null, null);

        assertTrue(showtimeResponses.isEmpty());
    }

    private List<Showtime> anyCollectionOfShowtime() {
        return any();
    }
}
