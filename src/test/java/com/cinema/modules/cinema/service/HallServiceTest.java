package com.cinema.modules.cinema.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.cinema.dto.request.HallRequest;
import com.cinema.modules.cinema.dto.response.HallResponse;
import com.cinema.modules.cinema.entity.Hall;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.cinema.repository.HallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HallServiceTest {

    private static final Long USER_ID = 4L;
    private static final Long CINEMA_ID = 1L;
    private static final Long HALL_ID = 8L;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private HallRepository hallRepository;

    @Mock
    private CinemaAccessGuard cinemaAccessGuard;

    @Mock
    private HallUsageChecker hallUsageChecker;

    @Mock
    private PublicCinemaCache publicCinemaCache;

    private HallService hallService;
    private Hall hall;

    @BeforeEach
    void setUp() {
        hallService = new HallService(
                hallRepository, cinemaAccessGuard, hallUsageChecker, publicCinemaCache, Clock.fixed(NOW, ZoneOffset.UTC));
        hall = new Hall();
        hall.setId(HALL_ID);
        hall.setCinemaId(CINEMA_ID);
        hall.setName("Phòng 01");
    }

    @Test
    void createHallRejectsDuplicateNameAmongActiveHalls() {
        when(hallRepository.existsByCinemaIdAndNameIgnoreCaseAndDeletedFalse(CINEMA_ID, "Phòng 01")).thenReturn(true);

        AppException appException = assertThrows(
                AppException.class, () -> hallService.createHall(USER_ID, CINEMA_ID, request("Phòng 01")));

        assertEquals("HALL_NAME_EXISTS", appException.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, appException.getStatus());
        verify(hallRepository, never()).saveAndFlush(any(Hall.class));
    }

    @Test
    void createHallStartsWithoutSeats() {
        when(hallRepository.existsByCinemaIdAndNameIgnoreCaseAndDeletedFalse(CINEMA_ID, "IMAX 1")).thenReturn(false);
        when(hallRepository.saveAndFlush(any(Hall.class))).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        HallResponse hallResponse = hallService.createHall(USER_ID, CINEMA_ID, request("  IMAX 1  "));

        assertEquals("IMAX 1", hallResponse.name());
        assertEquals(ScreenType.IMAX, hallResponse.screenType());
        assertEquals(0, hallResponse.totalSeats());
        assertFalse(hallResponse.deleted());
        verify(cinemaAccessGuard).requireAccessibleCinema(USER_ID, CINEMA_ID);
        verify(publicCinemaCache).evictAfterCommit();
    }

    @Test
    void listHallsFiltersByTheDeletedFlag() {
        hall.markDeleted(NOW);
        when(hallRepository.findByCinemaIdAndDeletedOrderByNameAsc(CINEMA_ID, true)).thenReturn(List.of(hall));

        List<HallResponse> hallResponses = hallService.listHalls(USER_ID, CINEMA_ID, true);

        assertEquals(1, hallResponses.size());
        assertTrue(hallResponses.get(0).deleted());
        verify(hallRepository, never()).findByCinemaIdAndDeletedOrderByNameAsc(CINEMA_ID, false);
    }

    @Test
    void deleteHallRefusesWhenUpcomingShowtimesExist() {
        when(cinemaAccessGuard.requireAccessibleHall(USER_ID, HALL_ID)).thenReturn(hall);
        when(hallUsageChecker.countUpcomingShowtimesByHall(HALL_ID, NOW)).thenReturn(2L);

        AppException appException = assertThrows(AppException.class, () -> hallService.deleteHall(USER_ID, HALL_ID));

        assertEquals("HALL_HAS_UPCOMING_SHOWTIMES", appException.getErrorCode());
        assertFalse(hall.isDeleted());
        verify(hallRepository, never()).saveAndFlush(any(Hall.class));
    }

    @Test
    void deleteHallIsASoftDeleteThatKeepsTheRow() {
        when(cinemaAccessGuard.requireAccessibleHall(USER_ID, HALL_ID)).thenReturn(hall);
        when(hallUsageChecker.countUpcomingShowtimesByHall(HALL_ID, NOW)).thenReturn(0L);

        hallService.deleteHall(USER_ID, HALL_ID);

        assertTrue(hall.isDeleted());
        assertEquals(NOW, hall.getDeletedAt());
        verify(hallRepository).saveAndFlush(hall);
        verify(hallRepository, never()).delete(any(Hall.class));
        verify(publicCinemaCache).evictAfterCommit();
    }

    @Test
    void restoreHallBringsItBackWhenTheNameIsFree() {
        hall.markDeleted(NOW);
        when(cinemaAccessGuard.requireDeletedHallOfActiveCinema(USER_ID, HALL_ID)).thenReturn(hall);
        when(hallRepository.existsByCinemaIdAndNameIgnoreCaseAndDeletedFalse(CINEMA_ID, "Phòng 01")).thenReturn(false);
        when(hallRepository.saveAndFlush(hall)).thenReturn(hall);

        HallResponse hallResponse = hallService.restoreHall(USER_ID, HALL_ID);

        assertFalse(hallResponse.deleted());
        assertFalse(hall.isDeleted());
        verify(publicCinemaCache).evictAfterCommit();
    }

    @Test
    void restoreHallRefusesWhenAnotherActiveHallTookTheName() {
        hall.markDeleted(NOW);
        when(cinemaAccessGuard.requireDeletedHallOfActiveCinema(USER_ID, HALL_ID)).thenReturn(hall);
        when(hallRepository.existsByCinemaIdAndNameIgnoreCaseAndDeletedFalse(CINEMA_ID, "Phòng 01")).thenReturn(true);

        AppException appException = assertThrows(AppException.class, () -> hallService.restoreHall(USER_ID, HALL_ID));

        assertEquals("HALL_NAME_EXISTS", appException.getErrorCode());
        assertTrue(hall.isDeleted());
    }

    private static HallRequest request(String name) {
        return new HallRequest(name, ScreenType.IMAX, HallStatus.ACTIVE);
    }
}
