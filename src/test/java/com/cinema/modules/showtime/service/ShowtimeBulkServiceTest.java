package com.cinema.modules.showtime.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.util.BusinessZone;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.showtime.dto.request.ShowtimeDayCopyRequest;
import com.cinema.modules.showtime.dto.request.ShowtimeDuplicateRequest;
import com.cinema.modules.showtime.dto.response.BulkShowtimeItemResponse;
import com.cinema.modules.showtime.dto.response.BulkShowtimeResponse;
import com.cinema.modules.showtime.dto.response.ShowtimeConflictResponse;
import com.cinema.modules.showtime.entity.Showtime;
import com.cinema.modules.showtime.enums.BulkItemStatus;
import com.cinema.modules.showtime.enums.ShowtimeConflictType;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import com.cinema.modules.showtime.exception.ShowtimeConflictException;
import com.cinema.modules.showtime.model.PlannedShowtimeSlot;
import com.cinema.modules.showtime.repository.ShowtimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowtimeBulkServiceTest {

    private static final Long USER_ID = 4L;
    private static final Long CINEMA_ID = 1L;
    private static final Long HALL_ID = 3L;
    private static final Long OTHER_HALL_ID = 4L;
    private static final Long MOVIE_ID = 9L;
    private static final Long SOURCE_ID = 70L;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDate SOURCE_DATE = LocalDate.of(2026, 1, 5);

    @Mock
    private ShowtimeRepository showtimeRepository;

    @Mock
    private ShowtimeSlotPlanner showtimeSlotPlanner;

    @Mock
    private ShowtimePersister showtimePersister;

    @Mock
    private ICinemaQueryService cinemaQueryService;

    @Mock
    private IBranchAccessService branchAccessService;

    private ShowtimeBulkService showtimeBulkService;

    @BeforeEach
    void setUp() {
        showtimeBulkService = new ShowtimeBulkService(
                showtimeRepository, showtimeSlotPlanner, showtimePersister, cinemaQueryService, branchAccessService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Instant vietnamInstant(LocalDate date, String time) {
        return BusinessZone.toInstant(date, LocalTime.parse(time));
    }

    private Showtime showtime(Long id, Long hallId, Instant startTime) {
        Showtime showtime = new Showtime();
        showtime.setId(id);
        showtime.setMovieId(MOVIE_ID);
        showtime.setHallId(hallId);
        showtime.setStartTime(startTime);
        showtime.setEndTime(startTime.plusSeconds(7200));
        showtime.setBasePrice(new BigDecimal("90000"));
        showtime.setStatus(ShowtimeStatus.ACTIVE);
        return showtime;
    }

    private HallInfoResponse hall(Long hallId, String name) {
        return new HallInfoResponse(hallId, CINEMA_ID, name, ScreenType.SCREEN_2D, HallStatus.ACTIVE, false);
    }

    private void givenPlannerAcceptsEverythingWithTwoHourMovies() {
        when(showtimeSlotPlanner.plan(anyLong(), anyLong(), anyLong(), any(Instant.class), any(Instant.class), isNull()))
                .thenAnswer(invocationOnMock -> {
                    Instant startTime = invocationOnMock.getArgument(3);
                    return new PlannedShowtimeSlot(startTime, startTime.plusSeconds(7200));
                });
    }

    private void givenSourceShowtimeAt(String time) {
        when(showtimeRepository.findById(SOURCE_ID))
                .thenReturn(Optional.of(showtime(SOURCE_ID, HALL_ID, vietnamInstant(SOURCE_DATE, time))));
    }

    private void givenHalls(Long... hallIds) {
        Map<Long, HallInfoResponse> hallInfoResponses = new HashMap<>();
        for (Long hallId : hallIds) {
            hallInfoResponses.put(hallId, hall(hallId, "Phòng " + hallId));
        }
        when(cinemaQueryService.findHallInfos(any())).thenReturn(hallInfoResponses);
    }

    private ShowtimeDuplicateRequest duplicateRequest(List<Long> hallIds, List<LocalDate> dates, boolean dryRun) {
        return new ShowtimeDuplicateRequest(SOURCE_ID, hallIds, dates, dryRun);
    }

    @Test
    void duplicatePreviewKeepsVietnamTimeOfDayAndSavesNothing() {
        givenSourceShowtimeAt("19:30");
        givenHalls(HALL_ID, OTHER_HALL_ID);
        givenPlannerAcceptsEverythingWithTwoHourMovies();
        List<LocalDate> dates = List.of(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 8));

        BulkShowtimeResponse bulkShowtimeResponse = showtimeBulkService.duplicateShowtime(
                USER_ID, duplicateRequest(List.of(HALL_ID, OTHER_HALL_ID), dates, true));

        assertEquals(4, bulkShowtimeResponse.totalCount());
        assertEquals(4, bulkShowtimeResponse.acceptedCount());
        assertTrue(bulkShowtimeResponse.dryRun());
        assertTrue(bulkShowtimeResponse.items().stream().allMatch(item -> item.status() == BulkItemStatus.READY));
        assertEquals(vietnamInstant(LocalDate.of(2026, 1, 7), "19:30"), bulkShowtimeResponse.items().get(0).startTime());
        assertEquals("Phòng 3", bulkShowtimeResponse.items().get(0).hallName());
        assertEquals("Phòng 4", bulkShowtimeResponse.items().get(1).hallName());
        assertEquals(Instant.parse("2026-01-07T12:30:00Z"), bulkShowtimeResponse.items().get(0).startTime());
        verifyNoInteractions(showtimePersister);
    }

    @Test
    void duplicateRealRunSavesEachAcceptedSlotWithSourceMovieAndPrice() {
        givenSourceShowtimeAt("19:30");
        givenHalls(HALL_ID);
        givenPlannerAcceptsEverythingWithTwoHourMovies();
        when(showtimePersister.saveWithoutOverlap(any(Showtime.class), any(PlannedShowtimeSlot.class), isNull()))
                .thenAnswer(invocationOnMock -> {
                    Showtime showtime = invocationOnMock.getArgument(0);
                    showtime.setId(500L);
                    return showtime;
                });

        BulkShowtimeResponse bulkShowtimeResponse = showtimeBulkService.duplicateShowtime(
                USER_ID, duplicateRequest(List.of(HALL_ID), List.of(LocalDate.of(2026, 1, 7)), false));

        ArgumentCaptor<Showtime> showtimeCaptor = ArgumentCaptor.forClass(Showtime.class);
        verify(showtimePersister).saveWithoutOverlap(showtimeCaptor.capture(), any(PlannedShowtimeSlot.class), isNull());
        assertEquals(MOVIE_ID, showtimeCaptor.getValue().getMovieId());
        assertEquals(0, new BigDecimal("90000").compareTo(showtimeCaptor.getValue().getBasePrice()));
        assertEquals(ShowtimeStatus.ACTIVE, showtimeCaptor.getValue().getStatus());
        assertEquals(BulkItemStatus.CREATED, bulkShowtimeResponse.items().get(0).status());
        assertEquals(500L, bulkShowtimeResponse.items().get(0).showtimeId());
    }

    @Test
    void duplicateSkipsConflictingAndInvalidDatesButKeepsTheRest() {
        givenSourceShowtimeAt("19:30");
        givenHalls(HALL_ID);
        Instant conflictingStart = vietnamInstant(LocalDate.of(2026, 1, 7), "19:30");
        Instant invalidStart = vietnamInstant(LocalDate.of(2026, 1, 8), "19:30");
        ShowtimeConflictResponse showtimeConflictResponse = new ShowtimeConflictResponse(
                41L, 8L, "Mai", conflictingStart, conflictingStart.plusSeconds(7200),
                conflictingStart.plusSeconds(8100), ShowtimeConflictType.OVERLAP);
        when(showtimeSlotPlanner.plan(anyLong(), anyLong(), anyLong(), any(Instant.class), any(Instant.class), isNull()))
                .thenAnswer(invocationOnMock -> {
                    Instant startTime = invocationOnMock.getArgument(3);
                    if (startTime.equals(conflictingStart)) {
                        throw new ShowtimeConflictException(List.of(showtimeConflictResponse));
                    }
                    if (startTime.equals(invalidStart)) {
                        throw new AppException("Phim đã ngừng chiếu, không thể xếp lịch chiếu", HttpStatus.CONFLICT, "MOVIE_ENDED");
                    }
                    return new PlannedShowtimeSlot(startTime, startTime.plusSeconds(7200));
                });

        BulkShowtimeResponse bulkShowtimeResponse = showtimeBulkService.duplicateShowtime(
                USER_ID,
                duplicateRequest(
                        List.of(HALL_ID),
                        List.of(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 9)),
                        true));

        assertEquals(3, bulkShowtimeResponse.totalCount());
        assertEquals(1, bulkShowtimeResponse.acceptedCount());
        assertEquals(1, bulkShowtimeResponse.conflictCount());
        assertEquals(1, bulkShowtimeResponse.invalidCount());
        BulkShowtimeItemResponse conflictItem = bulkShowtimeResponse.items().get(0);
        assertEquals(BulkItemStatus.CONFLICT, conflictItem.status());
        assertEquals(List.of(showtimeConflictResponse), conflictItem.conflicts());
        assertNull(conflictItem.endTime());
        BulkShowtimeItemResponse invalidItem = bulkShowtimeResponse.items().get(1);
        assertEquals(BulkItemStatus.INVALID, invalidItem.status());
        assertEquals("Phim đã ngừng chiếu, không thể xếp lịch chiếu", invalidItem.reason());
        assertEquals(BulkItemStatus.READY, bulkShowtimeResponse.items().get(2).status());
    }

    @Test
    void duplicateRealRunReportsRaceConflictFromThePersisterAndContinues() {
        givenSourceShowtimeAt("19:30");
        givenHalls(HALL_ID);
        givenPlannerAcceptsEverythingWithTwoHourMovies();
        Instant racedStart = vietnamInstant(LocalDate.of(2026, 1, 7), "19:30");
        when(showtimePersister.saveWithoutOverlap(any(Showtime.class), any(PlannedShowtimeSlot.class), isNull()))
                .thenAnswer(invocationOnMock -> {
                    Showtime showtime = invocationOnMock.getArgument(0);
                    if (showtime.getStartTime().equals(racedStart)) {
                        throw new ShowtimeConflictException(List.of());
                    }
                    showtime.setId(501L);
                    return showtime;
                });

        BulkShowtimeResponse bulkShowtimeResponse = showtimeBulkService.duplicateShowtime(
                USER_ID,
                duplicateRequest(List.of(HALL_ID), List.of(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 8)), false));

        assertEquals(BulkItemStatus.CONFLICT, bulkShowtimeResponse.items().get(0).status());
        assertEquals(BulkItemStatus.CREATED, bulkShowtimeResponse.items().get(1).status());
        assertEquals(1, bulkShowtimeResponse.acceptedCount());
    }

    @Test
    void duplicateIgnoresRepeatedDatesAndHalls() {
        givenSourceShowtimeAt("19:30");
        givenHalls(HALL_ID);
        givenPlannerAcceptsEverythingWithTwoHourMovies();
        LocalDate date = LocalDate.of(2026, 1, 7);

        BulkShowtimeResponse bulkShowtimeResponse = showtimeBulkService.duplicateShowtime(
                USER_ID, duplicateRequest(List.of(HALL_ID, HALL_ID), List.of(date, date), true));

        assertEquals(1, bulkShowtimeResponse.totalCount());
    }

    @Test
    void duplicateRejectsMoreThanTheCandidateLimit() {
        givenSourceShowtimeAt("19:30");
        Long[] hallIds = new Long[20];
        for (int i = 0; i < hallIds.length; i++) {
            hallIds[i] = 100L + i;
        }
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            dates.add(LocalDate.of(2026, 2, 1).plusDays(i));
        }

        AppException appException = assertThrows(AppException.class, () -> showtimeBulkService.duplicateShowtime(
                USER_ID, duplicateRequest(List.of(hallIds), dates, true)));

        assertEquals("INVALID_BULK_REQUEST", appException.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, appException.getStatus());
        verifyNoInteractions(showtimeSlotPlanner);
    }

    @Test
    void duplicateRejectsUnknownTargetHall() {
        givenSourceShowtimeAt("19:30");
        when(cinemaQueryService.findHallInfos(any())).thenReturn(Map.of(HALL_ID, hall(HALL_ID, "Phòng 3")));

        AppException appException = assertThrows(AppException.class, () -> showtimeBulkService.duplicateShowtime(
                USER_ID, duplicateRequest(List.of(999L), List.of(LocalDate.of(2026, 1, 7)), true)));

        assertEquals("HALL_NOT_FOUND", appException.getErrorCode());
        verifyNoInteractions(showtimeSlotPlanner);
    }

    @Test
    void duplicateStopsWholeRequestWhenActorCannotAccessATargetCinema() {
        givenSourceShowtimeAt("19:30");
        givenHalls(HALL_ID);
        doThrow(new AppException("Không có quyền", HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED"))
                .when(branchAccessService).assertCanAccessCinema(USER_ID, CINEMA_ID);

        AppException appException = assertThrows(AppException.class, () -> showtimeBulkService.duplicateShowtime(
                USER_ID, duplicateRequest(List.of(HALL_ID), List.of(LocalDate.of(2026, 1, 7)), false)));

        assertEquals("BRANCH_ACCESS_DENIED", appException.getErrorCode());
        verifyNoInteractions(showtimeSlotPlanner, showtimePersister);
    }

    @Test
    void duplicateOfUnknownSourceIsNotFound() {
        when(showtimeRepository.findById(SOURCE_ID)).thenReturn(Optional.empty());

        AppException appException = assertThrows(AppException.class, () -> showtimeBulkService.duplicateShowtime(
                USER_ID, duplicateRequest(List.of(HALL_ID), List.of(LocalDate.of(2026, 1, 7)), true)));

        assertEquals("SHOWTIME_NOT_FOUND", appException.getErrorCode());
    }

    @SuppressWarnings("unchecked")
    private void givenCinemaWithSourceDay(List<Showtime> sourceShowtimes) {
        when(cinemaQueryService.findAllCinemaIds()).thenReturn(Set.of(CINEMA_ID));
        when(cinemaQueryService.findHallIdsByCinemaIds(List.of(CINEMA_ID))).thenReturn(Set.of(HALL_ID, OTHER_HALL_ID));
        when(showtimeRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(sourceShowtimes);
    }

    private void givenCinemaHallInfos() {
        when(cinemaQueryService.findHallInfos(Set.of(HALL_ID, OTHER_HALL_ID))).thenReturn(Map.of(
                HALL_ID, hall(HALL_ID, "Phòng 3"), OTHER_HALL_ID, hall(OTHER_HALL_ID, "Phòng 4")));
    }

    @Test
    void copyDayShiftsEverySourceShowtimeToEachTargetDateKeepingHallAndLocalTime() {
        givenCinemaWithSourceDay(List.of(
                showtime(1L, HALL_ID, vietnamInstant(SOURCE_DATE, "09:00")),
                showtime(2L, OTHER_HALL_ID, vietnamInstant(SOURCE_DATE, "19:30"))));
        givenCinemaHallInfos();
        givenPlannerAcceptsEverythingWithTwoHourMovies();
        LocalDate firstTarget = LocalDate.of(2026, 1, 12);
        LocalDate secondTarget = LocalDate.of(2026, 1, 13);

        BulkShowtimeResponse bulkShowtimeResponse = showtimeBulkService.copyDay(
                USER_ID, new ShowtimeDayCopyRequest(CINEMA_ID, SOURCE_DATE, List.of(secondTarget, firstTarget), true));

        assertEquals(4, bulkShowtimeResponse.totalCount());
        assertEquals(vietnamInstant(firstTarget, "09:00"), bulkShowtimeResponse.items().get(0).startTime());
        assertEquals(HALL_ID, bulkShowtimeResponse.items().get(0).hallId());
        assertEquals(vietnamInstant(firstTarget, "19:30"), bulkShowtimeResponse.items().get(1).startTime());
        assertEquals(OTHER_HALL_ID, bulkShowtimeResponse.items().get(1).hallId());
        assertEquals(vietnamInstant(secondTarget, "09:00"), bulkShowtimeResponse.items().get(2).startTime());
        verifyNoInteractions(showtimePersister);
    }

    @Test
    void copyDayPreviewFlagsCopiesThatWouldCollideWithEachOtherAcrossMidnight() {
        givenCinemaWithSourceDay(List.of(
                showtime(1L, HALL_ID, vietnamInstant(SOURCE_DATE, "00:30")),
                showtime(2L, HALL_ID, vietnamInstant(SOURCE_DATE, "23:30"))));
        givenCinemaHallInfos();
        givenPlannerAcceptsEverythingWithTwoHourMovies();

        BulkShowtimeResponse bulkShowtimeResponse = showtimeBulkService.copyDay(
                USER_ID,
                new ShowtimeDayCopyRequest(
                        CINEMA_ID, SOURCE_DATE, List.of(LocalDate.of(2026, 1, 12), LocalDate.of(2026, 1, 13)), true));

        assertEquals(4, bulkShowtimeResponse.totalCount());
        assertEquals(3, bulkShowtimeResponse.acceptedCount());
        assertEquals(1, bulkShowtimeResponse.conflictCount());
        BulkShowtimeItemResponse collidingItem = bulkShowtimeResponse.items().get(2);
        assertEquals(vietnamInstant(LocalDate.of(2026, 1, 13), "00:30"), collidingItem.startTime());
        assertEquals(BulkItemStatus.CONFLICT, collidingItem.status());
    }

    @Test
    void copyDayRejectsTargetThatEqualsSourceDate() {
        AppException appException = assertThrows(AppException.class, () -> showtimeBulkService.copyDay(
                USER_ID, new ShowtimeDayCopyRequest(CINEMA_ID, SOURCE_DATE, List.of(SOURCE_DATE), true)));

        assertEquals("INVALID_BULK_REQUEST", appException.getErrorCode());
        verifyNoInteractions(showtimeSlotPlanner);
    }

    @Test
    void copyDayWithNoShowtimesOnSourceDayReturnsEmptyResult() {
        givenCinemaWithSourceDay(List.of());
        givenCinemaHallInfos();

        BulkShowtimeResponse bulkShowtimeResponse = showtimeBulkService.copyDay(
                USER_ID, new ShowtimeDayCopyRequest(CINEMA_ID, SOURCE_DATE, List.of(LocalDate.of(2026, 1, 12)), false));

        assertEquals(0, bulkShowtimeResponse.totalCount());
        assertTrue(bulkShowtimeResponse.items().isEmpty());
        verifyNoInteractions(showtimeSlotPlanner, showtimePersister);
    }

    @Test
    void copyDayChecksBranchAccessAndCinemaExistence() {
        doThrow(new AppException("Không có quyền", HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED"))
                .when(branchAccessService).assertCanAccessCinema(USER_ID, CINEMA_ID);

        AppException appException = assertThrows(AppException.class, () -> showtimeBulkService.copyDay(
                USER_ID, new ShowtimeDayCopyRequest(CINEMA_ID, SOURCE_DATE, List.of(LocalDate.of(2026, 1, 12)), true)));

        assertEquals("BRANCH_ACCESS_DENIED", appException.getErrorCode());
        verify(showtimeRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void copyDayRejectsMoreThanTheCandidateLimit() {
        List<Showtime> sourceShowtimes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            sourceShowtimes.add(showtime(100L + i, HALL_ID, vietnamInstant(SOURCE_DATE, "0" + i + ":00")));
        }
        givenCinemaWithSourceDay(sourceShowtimes);
        List<LocalDate> targetDates = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            targetDates.add(LocalDate.of(2026, 2, 1).plusDays(i));
        }

        AppException appException = assertThrows(AppException.class, () -> showtimeBulkService.copyDay(
                USER_ID, new ShowtimeDayCopyRequest(CINEMA_ID, SOURCE_DATE, targetDates, true)));

        assertEquals("INVALID_BULK_REQUEST", appException.getErrorCode());
    }
}
