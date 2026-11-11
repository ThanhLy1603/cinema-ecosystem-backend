package com.cinema.modules.cinema.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.cinema.dto.request.GenerateSeatsRequest;
import com.cinema.modules.cinema.dto.request.SeatCellRequest;
import com.cinema.modules.cinema.dto.request.SeatMapRequest;
import com.cinema.modules.cinema.dto.response.SeatLayoutResponse;
import com.cinema.modules.cinema.dto.response.SeatMapLockResponse;
import com.cinema.modules.cinema.dto.response.SeatLayoutSeatResponse;
import com.cinema.modules.cinema.entity.Hall;
import com.cinema.modules.cinema.entity.Seat;
import com.cinema.modules.cinema.entity.SeatType;
import com.cinema.modules.cinema.enums.SeatStatus;
import com.cinema.modules.cinema.repository.HallRepository;
import com.cinema.modules.cinema.repository.SeatRepository;
import com.cinema.modules.cinema.repository.SeatTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatMapServiceTest {

    private static final Long USER_ID = 9L;
    private static final Long HALL_ID = 5L;
    private static final Long CINEMA_ID = 2L;
    private static final long HALL_VERSION = 3L;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private HallRepository hallRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private SeatTypeRepository seatTypeRepository;

    @Mock
    private CinemaAccessGuard cinemaAccessGuard;

    @Mock
    private HallUsageChecker hallUsageChecker;

    @Mock
    private PublicCinemaCache publicCinemaCache;

    private SeatMapService seatMapService;
    private Hall hall;
    private SeatType standardSeatType;
    private SeatType vipSeatType;
    private SeatType sweetboxSeatType;

    @BeforeEach
    void setUp() {
        seatMapService = new SeatMapService(
                hallRepository,
                seatRepository,
                seatTypeRepository,
                cinemaAccessGuard,
                hallUsageChecker,
                publicCinemaCache,
                Clock.fixed(NOW, ZoneOffset.UTC));
        hall = new Hall();
        hall.setId(HALL_ID);
        hall.setCinemaId(CINEMA_ID);
        hall.setName("Phòng 01");
        standardSeatType = seatType(1L, "STANDARD", 0, 1);
        vipSeatType = seatType(2L, "VIP", 15000, 1);
        sweetboxSeatType = seatType(3L, "SWEETBOX", 45000, 2);
        lenient().when(cinemaAccessGuard.requireAccessibleHall(USER_ID, HALL_ID)).thenReturn(hall);
        lenient().when(seatTypeRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(standardSeatType, vipSeatType, sweetboxSeatType));
        lenient().when(hallRepository.saveAndFlush(hall)).thenReturn(hall);
        lenient().when(seatRepository.saveAll(anyList())).thenAnswer(invocationOnMock -> {
            List<Seat> seats = invocationOnMock.getArgument(0);
            long nextSeatId = 100L;
            for (Seat seat : seats) {
                if (seat.getId() == null) {
                    seat.setId(nextSeatId++);
                }
            }
            return seats;
        });
    }

    @Test
    void saveSeatMapRejectsStaleVersion() {
        hall.setVersion(HALL_VERSION);
        SeatMapRequest seatMapRequest = new SeatMapRequest(HALL_VERSION - 1, List.of(cell("A", 1, "STANDARD")));

        AppException appException =
                assertThrows(AppException.class, () -> seatMapService.saveSeatMap(USER_ID, HALL_ID, seatMapRequest));

        assertEquals("SEAT_MAP_CONFLICT", appException.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, appException.getStatus());
        verify(seatRepository, never()).saveAll(anyList());
    }

    @Test
    void saveSeatMapKeepsExistingSeatIdsAndInsertsNewSeats() {
        hall.setVersion(HALL_VERSION);
        Seat existingSeat = seat(11L, "A", 1, standardSeatType);
        when(seatRepository.findAllByHallIdAndDeletedFalseOrderBySeatRowAscSeatNumberAsc(HALL_ID)).thenReturn(List.of(existingSeat));
        SeatMapRequest seatMapRequest =
                new SeatMapRequest(HALL_VERSION, List.of(cell("A", 1, "VIP"), cell("A", 2, "STANDARD")));

        SeatLayoutResponse seatLayoutResponse = seatMapService.saveSeatMap(USER_ID, HALL_ID, seatMapRequest);

        assertEquals(2, seatLayoutResponse.seats().size());
        assertEquals(11L, seatLayoutResponse.seats().get(0).id());
        assertEquals("VIP", seatLayoutResponse.seats().get(0).type());
        assertNotNull(seatLayoutResponse.seats().get(1).id());
        assertEquals(2, hall.getTotalSeats());
        assertEquals(NOW, hall.getSeatMapUpdatedAt());
        assertFalse(existingSeat.isDeleted());
        verify(publicCinemaCache).evictAfterCommit();
    }

    @Test
    void saveSeatMapSoftDeletesRemovedSeatsWhenNoShowtimeIsScheduled() {
        hall.setVersion(HALL_VERSION);
        Seat keptSeat = seat(11L, "A", 1, standardSeatType);
        Seat removedSeat = seat(12L, "A", 2, standardSeatType);
        when(seatRepository.findAllByHallIdAndDeletedFalseOrderBySeatRowAscSeatNumberAsc(HALL_ID))
                .thenReturn(List.of(keptSeat, removedSeat));
        when(hallUsageChecker.countUpcomingShowtimesByHall(HALL_ID, NOW)).thenReturn(0L);
        SeatMapRequest seatMapRequest = new SeatMapRequest(HALL_VERSION, List.of(cell("A", 1, "STANDARD")));

        SeatLayoutResponse seatLayoutResponse = seatMapService.saveSeatMap(USER_ID, HALL_ID, seatMapRequest);

        assertEquals(1, seatLayoutResponse.seats().size());
        assertEquals(1, hall.getTotalSeats());
        assertTrue(removedSeat.isDeleted());
        assertEquals(NOW, removedSeat.getDeletedAt());
        assertFalse(keptSeat.isDeleted());
        verify(seatRepository, never()).deleteAllByIdInBatch(any());
        verify(seatRepository, never()).delete(any(Seat.class));
    }

    @Test
    void saveSeatMapIsLockedWhileAnyShowtimeIsScheduledEvenWithoutBookings() {
        hall.setVersion(HALL_VERSION);
        Seat existingSeat = seat(11L, "A", 1, standardSeatType);
        when(hallUsageChecker.countUpcomingShowtimesByHall(HALL_ID, NOW)).thenReturn(2L);
        SeatMapRequest seatMapRequest = new SeatMapRequest(HALL_VERSION, List.of(cell("A", 1, "VIP")));

        AppException appException =
                assertThrows(AppException.class, () -> seatMapService.saveSeatMap(USER_ID, HALL_ID, seatMapRequest));

        assertEquals("SEAT_MAP_LOCKED", appException.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, appException.getStatus());
        assertTrue(appException.getMessage().contains("2 suất chiếu"));
        assertFalse(existingSeat.isDeleted());
        verify(seatRepository, never()).saveAll(anyList());
        verify(hallRepository, never()).saveAndFlush(any(Hall.class));
        verify(publicCinemaCache, never()).evictAfterCommit();
    }

    @Test
    void saveSeatMapIsLockedEvenForChangesThatOnlyTouchSeatTypeOrStatus() {
        hall.setVersion(HALL_VERSION);
        when(hallUsageChecker.countUpcomingShowtimesByHall(HALL_ID, NOW)).thenReturn(1L);
        SeatMapRequest seatMapRequest = new SeatMapRequest(
                HALL_VERSION, List.of(new SeatCellRequest("A", 1, "STANDARD", SeatStatus.MAINTENANCE)));

        AppException appException =
                assertThrows(AppException.class, () -> seatMapService.saveSeatMap(USER_ID, HALL_ID, seatMapRequest));

        assertEquals("SEAT_MAP_LOCKED", appException.getErrorCode());
    }

    @Test
    void seatMapLockReportsScheduledAndBookedShowtimeCounts() {
        when(hallUsageChecker.countUpcomingShowtimesByHall(HALL_ID, NOW)).thenReturn(3L);
        when(hallUsageChecker.countShowtimesWithBookingsByHall(HALL_ID, NOW)).thenReturn(1L);

        SeatMapLockResponse seatMapLockResponse = seatMapService.getSeatMapLock(USER_ID, HALL_ID);

        assertTrue(seatMapLockResponse.locked());
        assertEquals(3L, seatMapLockResponse.upcomingShowtimeCount());
        assertEquals(1L, seatMapLockResponse.bookedShowtimeCount());
    }

    @Test
    void seatMapIsNotLockedWhenNoShowtimeIsScheduled() {
        when(hallUsageChecker.countUpcomingShowtimesByHall(HALL_ID, NOW)).thenReturn(0L);

        SeatMapLockResponse seatMapLockResponse = seatMapService.getSeatMapLock(USER_ID, HALL_ID);

        assertFalse(seatMapLockResponse.locked());
        assertEquals(0L, seatMapLockResponse.bookedShowtimeCount());
        verify(hallUsageChecker, never()).countShowtimesWithBookingsByHall(any(), any());
    }

    @Test
    void generateSeatsBuildsTenByTenGridWithoutDuplicates() {
        when(seatRepository.countByHallIdAndDeletedFalse(HALL_ID)).thenReturn(0L);
        when(seatRepository.findAllByHallIdAndDeletedFalseOrderBySeatRowAscSeatNumberAsc(HALL_ID)).thenReturn(List.of());

        SeatLayoutResponse seatLayoutResponse =
                seatMapService.generateSeats(USER_ID, HALL_ID, new GenerateSeatsRequest(10, 10, "STANDARD"));

        ArgumentCaptor<List<Seat>> seatsCaptor = ArgumentCaptor.forClass(List.class);
        verify(seatRepository).saveAll(seatsCaptor.capture());
        Set<String> labels = new HashSet<>();
        seatsCaptor.getValue().forEach(seat -> labels.add(seat.getSeatRow() + seat.getSeatNumber()));
        assertEquals(100, seatsCaptor.getValue().size());
        assertEquals(100, labels.size());
        assertEquals(100, hall.getTotalSeats());
        assertEquals(List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"), seatLayoutResponse.rows());
        assertEquals(10, seatLayoutResponse.columns());
    }

    @Test
    void generateSeatsWithDoubleSeatsSkipsTheCoveredColumn() {
        when(seatRepository.countByHallIdAndDeletedFalse(HALL_ID)).thenReturn(0L);
        when(seatRepository.findAllByHallIdAndDeletedFalseOrderBySeatRowAscSeatNumberAsc(HALL_ID)).thenReturn(List.of());

        SeatLayoutResponse seatLayoutResponse =
                seatMapService.generateSeats(USER_ID, HALL_ID, new GenerateSeatsRequest(1, 6, "SWEETBOX"));

        assertEquals(3, seatLayoutResponse.seats().size());
        assertEquals(List.of(1, 3, 5), seatLayoutResponse.seats().stream().map(SeatLayoutSeatResponse::number).toList());
        assertEquals(6, seatLayoutResponse.columns());
    }

    @Test
    void generateSeatsRefusesHallThatAlreadyHasSeats() {
        when(seatRepository.countByHallIdAndDeletedFalse(HALL_ID)).thenReturn(4L);

        AppException appException = assertThrows(
                AppException.class,
                () -> seatMapService.generateSeats(USER_ID, HALL_ID, new GenerateSeatsRequest(2, 2, "STANDARD")));

        assertEquals("HALL_ALREADY_HAS_SEATS", appException.getErrorCode());
        verify(seatRepository, never()).saveAll(anyList());
    }

    private static SeatCellRequest cell(String row, int number, String seatTypeCode) {
        return new SeatCellRequest(row, number, seatTypeCode, SeatStatus.ACTIVE);
    }

    private static SeatType seatType(Long id, String code, int surcharge, int seatSpan) {
        SeatType seatType = new SeatType();
        seatType.setId(id);
        seatType.setCode(code);
        seatType.setName(code);
        seatType.setDefaultSurcharge(BigDecimal.valueOf(surcharge));
        seatType.setSeatSpan(seatSpan);
        return seatType;
    }

    private static Seat seat(Long id, String row, int number, SeatType seatType) {
        Seat seat = new Seat();
        seat.setId(id);
        seat.setHallId(HALL_ID);
        seat.setSeatRow(row);
        seat.setSeatNumber(number);
        seat.setSeatType(seatType);
        seat.setStatus(SeatStatus.ACTIVE);
        return seat;
    }
}
