package com.cinema.modules.cinema.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.cinema.dto.request.CinemaRequest;
import com.cinema.modules.cinema.dto.response.CinemaDetailResponse;
import com.cinema.modules.cinema.dto.response.CinemaResponse;
import com.cinema.modules.cinema.entity.Cinema;
import com.cinema.modules.cinema.enums.CinemaStatus;
import com.cinema.modules.cinema.repository.CinemaFacilityRepository;
import com.cinema.modules.cinema.repository.CinemaRepository;
import com.cinema.modules.cinema.repository.HallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CinemaAdminServiceTest {

    private static final Long USER_ID = 4L;
    private static final Long CINEMA_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private CinemaRepository cinemaRepository;

    @Mock
    private CinemaFacilityRepository cinemaFacilityRepository;

    @Mock
    private HallRepository hallRepository;

    @Mock
    private IBranchAccessService branchAccessService;

    @Mock
    private CinemaAccessGuard cinemaAccessGuard;

    @Mock
    private HallUsageChecker hallUsageChecker;

    @Mock
    private PublicCinemaCache publicCinemaCache;

    private CinemaAdminService cinemaAdminService;
    private Cinema cinema;

    @BeforeEach
    void setUp() {
        cinemaAdminService = new CinemaAdminService(
                cinemaRepository,
                cinemaFacilityRepository,
                hallRepository,
                branchAccessService,
                cinemaAccessGuard,
                hallUsageChecker,
                publicCinemaCache,
                Clock.fixed(NOW, ZoneOffset.UTC));
        cinema = new Cinema();
        cinema.setId(CINEMA_ID);
        cinema.setName("Star Cineplex Bitexco");
        cinema.setSlug("star-cineplex-bitexco");
        cinema.setCity("Hồ Chí Minh");
        cinema.setAddress("Q1");
        cinema.setStatus(CinemaStatus.ACTIVE);
        lenient().when(hallRepository.findByCinemaIdOrderByNameAsc(any())).thenReturn(List.of());
        lenient().when(cinemaRepository.saveAndFlush(any(Cinema.class)))
                .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    @Test
    void createCinemaAddsSuffixWhenSlugIsTaken() {
        when(cinemaRepository.existsBySlug("rap-moi")).thenReturn(true);
        when(cinemaRepository.existsBySlug("rap-moi-2")).thenReturn(false);

        CinemaDetailResponse cinemaDetailResponse =
                cinemaAdminService.createCinema(request("Rạp Mới", CinemaStatus.ACTIVE, null, null));

        assertEquals("rap-moi-2", cinemaDetailResponse.slug());
        verify(publicCinemaCache).evictAfterCommit();
    }

    @Test
    void createCinemaRejectsLatitudeWithoutLongitude() {
        CinemaRequest cinemaRequest = request("Rạp Mới", CinemaStatus.ACTIVE, BigDecimal.TEN, null);

        AppException appException =
                assertThrows(AppException.class, () -> cinemaAdminService.createCinema(cinemaRequest));

        assertEquals("INVALID_COORDINATES", appException.getErrorCode());
        verify(cinemaRepository, never()).saveAndFlush(any(Cinema.class));
    }

    @Test
    void createCinemaRejectsDeletedStatus() {
        CinemaRequest cinemaRequest = request("Rạp Mới", CinemaStatus.DELETED, null, null);

        AppException appException =
                assertThrows(AppException.class, () -> cinemaAdminService.createCinema(cinemaRequest));

        assertEquals("INVALID_STATUS", appException.getErrorCode());
    }

    @Test
    void createCinemaRejectsUnknownFacility() {
        CinemaRequest cinemaRequest = new CinemaRequest(
                "Rạp Mới", "Hà Nội", "Địa chỉ", null, null, null, CinemaStatus.ACTIVE, List.of(1L, 2L));
        when(cinemaFacilityRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of());

        AppException appException =
                assertThrows(AppException.class, () -> cinemaAdminService.createCinema(cinemaRequest));

        assertEquals("INVALID_FACILITY", appException.getErrorCode());
    }

    @Test
    void managerCannotChangeCinemaStatus() {
        when(cinemaRepository.findWithFacilitiesById(CINEMA_ID)).thenReturn(Optional.of(cinema));
        when(cinemaAccessGuard.isGlobalAdmin(USER_ID)).thenReturn(false);
        CinemaRequest cinemaRequest = request(cinema.getName(), CinemaStatus.INACTIVE, null, null);

        AppException appException = assertThrows(
                AppException.class, () -> cinemaAdminService.updateCinema(USER_ID, CINEMA_ID, cinemaRequest));

        assertEquals("ADMIN_ONLY", appException.getErrorCode());
        assertEquals(HttpStatus.FORBIDDEN, appException.getStatus());
        verify(cinemaRepository, never()).saveAndFlush(any(Cinema.class));
    }

    @Test
    void managerCanEditCinemaInfoWithoutChangingStatus() {
        when(cinemaRepository.findWithFacilitiesById(CINEMA_ID)).thenReturn(Optional.of(cinema));
        CinemaRequest cinemaRequest = request("Tên mới", CinemaStatus.ACTIVE, null, null);

        CinemaDetailResponse cinemaDetailResponse = cinemaAdminService.updateCinema(USER_ID, CINEMA_ID, cinemaRequest);

        assertEquals("Tên mới", cinemaDetailResponse.name());
        assertEquals("star-cineplex-bitexco", cinemaDetailResponse.slug());
    }

    @Test
    void deleteCinemaRefusesWhenShowtimesAreStillUpcoming() {
        when(cinemaRepository.findById(CINEMA_ID)).thenReturn(Optional.of(cinema));
        when(hallUsageChecker.countUpcomingShowtimes(eq(CINEMA_ID), eq(NOW))).thenReturn(3L);

        AppException appException =
                assertThrows(AppException.class, () -> cinemaAdminService.deleteCinema(CINEMA_ID));

        assertEquals("CINEMA_HAS_UPCOMING_SHOWTIMES", appException.getErrorCode());
        assertEquals(CinemaStatus.ACTIVE, cinema.getStatus());
    }

    @Test
    void deleteCinemaIsASoftDelete() {
        when(cinemaRepository.findById(CINEMA_ID)).thenReturn(Optional.of(cinema));
        when(hallUsageChecker.countUpcomingShowtimes(eq(CINEMA_ID), eq(NOW))).thenReturn(0L);

        cinemaAdminService.deleteCinema(CINEMA_ID);

        assertEquals(CinemaStatus.DELETED, cinema.getStatus());
        verify(cinemaRepository).save(cinema);
        verify(publicCinemaCache).evictAfterCommit();
    }

    @Test
    void searchScopesManagersToTheirOwnCinemas() {
        when(branchAccessService.hasAccessToAllCinemas(USER_ID)).thenReturn(false);
        when(branchAccessService.getAccessibleCinemaIds(USER_ID)).thenReturn(Set.of(CINEMA_ID));
        Page<Cinema> cinemaPage = new PageImpl<>(List.of(cinema));
        when(cinemaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(cinemaPage);
        when(cinemaRepository.findWithFacilitiesByIdIn(List.of(CINEMA_ID))).thenReturn(List.of(cinema));

        PageResponse<CinemaResponse> pageResponse =
                cinemaAdminService.searchCinemas(USER_ID, null, null, null, 0, 20);

        assertEquals(1, pageResponse.content().size());
        verify(branchAccessService).getAccessibleCinemaIds(USER_ID);
    }

    @Test
    void searchCapsThePageSize() {
        when(branchAccessService.hasAccessToAllCinemas(USER_ID)).thenReturn(true);
        Page<Cinema> cinemaPage = new PageImpl<>(List.of());
        when(cinemaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(cinemaPage);

        PageResponse<CinemaResponse> pageResponse =
                cinemaAdminService.searchCinemas(USER_ID, null, null, null, 0, 10_000);

        assertEquals(0, pageResponse.content().size());
        verify(branchAccessService, never()).getAccessibleCinemaIds(USER_ID);
    }

    private static CinemaRequest request(
            String name, CinemaStatus status, BigDecimal latitude, BigDecimal longitude) {
        return new CinemaRequest(name, "Hồ Chí Minh", "Địa chỉ", "0281234567", latitude, longitude, status, null);
    }
}
