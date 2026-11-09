package com.cinema.modules.cinema.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.cinema.dto.response.CinemaResponse;
import com.cinema.modules.cinema.dto.response.SeatLayoutResponse;
import com.cinema.modules.cinema.entity.Cinema;
import com.cinema.modules.cinema.entity.Hall;
import com.cinema.modules.cinema.enums.CinemaStatus;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.repository.CinemaRepository;
import com.cinema.modules.cinema.repository.HallRepository;
import com.cinema.modules.cinema.repository.SeatRepository;
import com.cinema.modules.cinema.repository.SeatTypeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CinemaPublicServiceTest {

    @Mock
    private CinemaRepository cinemaRepository;

    @Mock
    private HallRepository hallRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private SeatTypeRepository seatTypeRepository;

    @Mock
    private PublicCinemaCache publicCinemaCache;

    private CinemaPublicService cinemaPublicService;

    @BeforeEach
    void setUp() {
        cinemaPublicService = new CinemaPublicService(
                cinemaRepository, hallRepository, seatRepository, seatTypeRepository, publicCinemaCache);
        lenient().when(publicCinemaCache.getOrLoad(anyString(), any(TypeReference.class), any(Supplier.class)))
                .thenAnswer(invocationOnMock -> ((Supplier<?>) invocationOnMock.getArgument(2)).get());
    }

    @Test
    void unknownCityReturnsEmptyListWithoutQueryingCinemas() {
        when(cinemaRepository.findDistinctCitiesByStatus(CinemaStatus.ACTIVE)).thenReturn(List.of("Hà Nội"));

        List<CinemaResponse> cinemaResponses = cinemaPublicService.findPublicCinemas("Gotham");

        assertEquals(0, cinemaResponses.size());
        verify(cinemaRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void knownCityReturnsActiveCinemasWithFacilitiesAndActiveHallCount() {
        Cinema cinema = new Cinema();
        cinema.setId(1L);
        cinema.setName("Rạp A");
        cinema.setCity("Hà Nội");
        Hall activeHall = hall(10L, HallStatus.ACTIVE);
        Hall maintenanceHall = hall(11L, HallStatus.MAINTENANCE);
        when(cinemaRepository.findDistinctCitiesByStatus(CinemaStatus.ACTIVE)).thenReturn(List.of("Hà Nội"));
        when(cinemaRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(cinema));
        when(cinemaRepository.findWithFacilitiesByIdIn(List.of(1L))).thenReturn(List.of(cinema));
        when(hallRepository.findByCinemaIdInOrderByNameAsc(List.of(1L)))
                .thenReturn(List.of(activeHall, maintenanceHall));

        List<CinemaResponse> cinemaResponses = cinemaPublicService.findPublicCinemas(" Hà Nội ");

        assertEquals(1, cinemaResponses.size());
        assertEquals(1, cinemaResponses.get(0).hallCount());
    }

    @Test
    void seatLayoutOfAHallUnderMaintenanceIsNotPublic() {
        when(hallRepository.findById(10L)).thenReturn(Optional.of(hall(10L, HallStatus.MAINTENANCE)));

        AppException appException =
                assertThrows(AppException.class, () -> cinemaPublicService.findPublicSeatLayout(10L));

        assertEquals("HALL_NOT_FOUND", appException.getErrorCode());
    }

    @Test
    void seatLayoutOfAHallInAnInactiveCinemaIsNotPublic() {
        Cinema inactiveCinema = new Cinema();
        inactiveCinema.setId(1L);
        inactiveCinema.setStatus(CinemaStatus.INACTIVE);
        when(hallRepository.findById(10L)).thenReturn(Optional.of(hall(10L, HallStatus.ACTIVE)));
        when(cinemaRepository.findById(1L)).thenReturn(Optional.of(inactiveCinema));

        AppException appException =
                assertThrows(AppException.class, () -> cinemaPublicService.findPublicSeatLayout(10L));

        assertEquals("HALL_NOT_FOUND", appException.getErrorCode());
    }

    @Test
    void seatLayoutOfAnActiveHallIsReturned() {
        Cinema activeCinema = new Cinema();
        activeCinema.setId(1L);
        activeCinema.setStatus(CinemaStatus.ACTIVE);
        when(hallRepository.findById(10L)).thenReturn(Optional.of(hall(10L, HallStatus.ACTIVE)));
        when(cinemaRepository.findById(1L)).thenReturn(Optional.of(activeCinema));
        when(seatRepository.findAllByHallIdOrderBySeatRowAscSeatNumberAsc(10L)).thenReturn(List.of());

        SeatLayoutResponse seatLayoutResponse = cinemaPublicService.findPublicSeatLayout(10L);

        assertEquals(10L, seatLayoutResponse.hallId());
        assertEquals(0, seatLayoutResponse.columns());
    }

    private static Hall hall(Long id, HallStatus status) {
        Hall hall = new Hall();
        hall.setId(id);
        hall.setCinemaId(1L);
        hall.setName("Phòng " + id);
        hall.setStatus(status);
        return hall;
    }
}
