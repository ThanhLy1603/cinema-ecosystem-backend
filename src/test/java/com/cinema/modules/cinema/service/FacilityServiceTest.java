package com.cinema.modules.cinema.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.cinema.dto.response.FacilityResponse;
import com.cinema.modules.cinema.entity.CinemaFacility;
import com.cinema.modules.cinema.repository.CinemaFacilityRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityServiceTest {

    private static final Long FACILITY_ID = 3L;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private CinemaFacilityRepository cinemaFacilityRepository;

    @Mock
    private PublicCinemaCache publicCinemaCache;

    private FacilityService facilityService;
    private CinemaFacility cinemaFacility;

    @BeforeEach
    void setUp() {
        facilityService = new FacilityService(
                cinemaFacilityRepository, publicCinemaCache, Clock.fixed(NOW, ZoneOffset.UTC));
        cinemaFacility = new CinemaFacility();
        cinemaFacility.setId(FACILITY_ID);
        cinemaFacility.setName("Bãi đỗ xe ô tô");
    }

    @Test
    void listFiltersByTheDeletedFlag() {
        cinemaFacility.markDeleted(NOW);
        when(cinemaFacilityRepository.findAllByDeletedOrderByNameAsc(true)).thenReturn(List.of(cinemaFacility));

        List<FacilityResponse> facilityResponses = facilityService.listFacilities(true);

        assertEquals(1, facilityResponses.size());
        assertTrue(facilityResponses.get(0).deleted());
    }

    @Test
    void deleteIsASoftDelete() {
        when(cinemaFacilityRepository.findByIdAndDeleted(FACILITY_ID, false)).thenReturn(Optional.of(cinemaFacility));

        facilityService.deleteFacility(FACILITY_ID);

        assertTrue(cinemaFacility.isDeleted());
        assertEquals(NOW, cinemaFacility.getDeletedAt());
        verify(cinemaFacilityRepository).saveAndFlush(cinemaFacility);
        verify(cinemaFacilityRepository, never()).delete(any(CinemaFacility.class));
        verify(publicCinemaCache).evictAfterCommit();
    }

    @Test
    void deletingAnAlreadyDeletedFacilityIsNotFound() {
        when(cinemaFacilityRepository.findByIdAndDeleted(FACILITY_ID, false)).thenReturn(Optional.empty());

        AppException appException =
                assertThrows(AppException.class, () -> facilityService.deleteFacility(FACILITY_ID));

        assertEquals("FACILITY_NOT_FOUND", appException.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, appException.getStatus());
    }

    @Test
    void restoreRefusesWhenAnActiveFacilityUsesTheName() {
        cinemaFacility.markDeleted(NOW);
        when(cinemaFacilityRepository.findByIdAndDeleted(FACILITY_ID, true)).thenReturn(Optional.of(cinemaFacility));
        when(cinemaFacilityRepository.existsByNameIgnoreCaseAndDeletedFalse("Bãi đỗ xe ô tô")).thenReturn(true);

        AppException appException =
                assertThrows(AppException.class, () -> facilityService.restoreFacility(FACILITY_ID));

        assertEquals("FACILITY_NAME_EXISTS", appException.getErrorCode());
        assertTrue(cinemaFacility.isDeleted());
    }

    @Test
    void restoreBringsTheFacilityBack() {
        cinemaFacility.markDeleted(NOW);
        when(cinemaFacilityRepository.findByIdAndDeleted(FACILITY_ID, true)).thenReturn(Optional.of(cinemaFacility));
        when(cinemaFacilityRepository.existsByNameIgnoreCaseAndDeletedFalse("Bãi đỗ xe ô tô")).thenReturn(false);
        when(cinemaFacilityRepository.saveAndFlush(cinemaFacility)).thenReturn(cinemaFacility);

        FacilityResponse facilityResponse = facilityService.restoreFacility(FACILITY_ID);

        assertFalse(facilityResponse.deleted());
        verify(publicCinemaCache).evictAfterCommit();
    }
}
