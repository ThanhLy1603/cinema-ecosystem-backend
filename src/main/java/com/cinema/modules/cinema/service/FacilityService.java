package com.cinema.modules.cinema.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.cinema.dto.request.FacilityRequest;
import com.cinema.modules.cinema.dto.response.FacilityResponse;
import com.cinema.modules.cinema.entity.CinemaFacility;
import com.cinema.modules.cinema.repository.CinemaFacilityRepository;
import com.cinema.modules.cinema.service.interfaces.IFacilityService;
import com.cinema.modules.cinema.util.CinemaMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class FacilityService implements IFacilityService {

    private final CinemaFacilityRepository cinemaFacilityRepository;
    private final PublicCinemaCache publicCinemaCache;
    private final Clock clock;

    public FacilityService(
            CinemaFacilityRepository cinemaFacilityRepository, PublicCinemaCache publicCinemaCache, Clock clock) {
        this.cinemaFacilityRepository = cinemaFacilityRepository;
        this.publicCinemaCache = publicCinemaCache;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public List<FacilityResponse> listFacilities(boolean deleted) {
        return cinemaFacilityRepository.findAllByDeletedOrderByNameAsc(deleted).stream()
                .map(CinemaMapper::toFacilityResponse)
                .toList();
    }

    @Transactional
    @Override
    public FacilityResponse createFacility(FacilityRequest facilityRequest) {
        String name = facilityRequest.name().trim();
        if (cinemaFacilityRepository.existsByNameIgnoreCaseAndDeletedFalse(name)) {
            throw nameAlreadyUsed();
        }
        CinemaFacility cinemaFacility = new CinemaFacility();
        applyRequest(cinemaFacility, facilityRequest);
        return saveAndEvict(cinemaFacility);
    }

    @Transactional
    @Override
    public FacilityResponse updateFacility(Long facilityId, FacilityRequest facilityRequest) {
        CinemaFacility cinemaFacility = loadFacility(facilityId, false);
        String name = facilityRequest.name().trim();
        if (cinemaFacilityRepository.existsByNameIgnoreCaseAndIdNotAndDeletedFalse(name, facilityId)) {
            throw nameAlreadyUsed();
        }
        applyRequest(cinemaFacility, facilityRequest);
        return saveAndEvict(cinemaFacility);
    }

    @Transactional
    @Override
    public void deleteFacility(Long facilityId) {
        CinemaFacility cinemaFacility = loadFacility(facilityId, false);
        cinemaFacility.markDeleted(Instant.now(clock));
        cinemaFacilityRepository.saveAndFlush(cinemaFacility);
        publicCinemaCache.evictAfterCommit();
    }

    @Transactional
    @Override
    public FacilityResponse restoreFacility(Long facilityId) {
        CinemaFacility cinemaFacility = loadFacility(facilityId, true);
        if (cinemaFacilityRepository.existsByNameIgnoreCaseAndDeletedFalse(cinemaFacility.getName())) {
            throw nameAlreadyUsed();
        }
        cinemaFacility.restore();
        return saveAndEvict(cinemaFacility);
    }

    private FacilityResponse saveAndEvict(CinemaFacility cinemaFacility) {
        try {
            CinemaFacility savedCinemaFacility = cinemaFacilityRepository.saveAndFlush(cinemaFacility);
            publicCinemaCache.evictAfterCommit();
            return CinemaMapper.toFacilityResponse(savedCinemaFacility);
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            throw nameAlreadyUsed();
        }
    }

    private void applyRequest(CinemaFacility cinemaFacility, FacilityRequest facilityRequest) {
        cinemaFacility.setName(facilityRequest.name().trim());
        cinemaFacility.setIconUrl(blankToNull(facilityRequest.iconUrl()));
        cinemaFacility.setDescription(blankToNull(facilityRequest.description()));
    }

    private CinemaFacility loadFacility(Long facilityId, boolean deleted) {
        return cinemaFacilityRepository.findByIdAndDeleted(facilityId, deleted)
                .orElseThrow(() -> new AppException(
                        "Không tìm thấy tiện ích", HttpStatus.NOT_FOUND, "FACILITY_NOT_FOUND"));
    }

    private static AppException nameAlreadyUsed() {
        return new AppException("Tên tiện ích đã tồn tại", HttpStatus.CONFLICT, "FACILITY_NAME_EXISTS");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
