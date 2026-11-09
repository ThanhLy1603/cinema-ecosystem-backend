package com.cinema.modules.cinema.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.cinema.dto.response.CinemaDetailResponse;
import com.cinema.modules.cinema.dto.response.CinemaResponse;
import com.cinema.modules.cinema.dto.response.SeatLayoutResponse;
import com.cinema.modules.cinema.dto.response.SeatTypeResponse;
import com.cinema.modules.cinema.entity.Cinema;
import com.cinema.modules.cinema.entity.Hall;
import com.cinema.modules.cinema.enums.CinemaStatus;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.repository.CinemaRepository;
import com.cinema.modules.cinema.repository.HallRepository;
import com.cinema.modules.cinema.repository.SeatRepository;
import com.cinema.modules.cinema.repository.SeatTypeRepository;
import com.cinema.modules.cinema.service.interfaces.ICinemaPublicService;
import com.cinema.modules.cinema.specification.CinemaSpecifications;
import com.cinema.modules.cinema.util.CinemaMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CinemaPublicService implements ICinemaPublicService {

    private static final String ALL_CITIES_CACHE_SUFFIX = "*";

    private final CinemaRepository cinemaRepository;
    private final HallRepository hallRepository;
    private final SeatRepository seatRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final PublicCinemaCache publicCinemaCache;

    public CinemaPublicService(
            CinemaRepository cinemaRepository,
            HallRepository hallRepository,
            SeatRepository seatRepository,
            SeatTypeRepository seatTypeRepository,
            PublicCinemaCache publicCinemaCache) {
        this.cinemaRepository = cinemaRepository;
        this.hallRepository = hallRepository;
        this.seatRepository = seatRepository;
        this.seatTypeRepository = seatTypeRepository;
        this.publicCinemaCache = publicCinemaCache;
    }

    @Transactional(readOnly = true)
    @Override
    public List<CinemaResponse> findPublicCinemas(String city) {
        String normalizedCity = city == null || city.isBlank() ? null : city.trim();
        if (normalizedCity != null && !findPublicCities().contains(normalizedCity)) {
            return Collections.emptyList();
        }
        String cacheName = "list:" + (normalizedCity == null ? ALL_CITIES_CACHE_SUFFIX : normalizedCity);
        return publicCinemaCache.getOrLoad(
                cacheName, new TypeReference<List<CinemaResponse>>() { }, () -> loadPublicCinemas(normalizedCity));
    }

    @Transactional(readOnly = true)
    @Override
    public CinemaDetailResponse findPublicCinemaBySlug(String slug) {
        return publicCinemaCache.getOrLoad(
                "detail:" + slug, new TypeReference<CinemaDetailResponse>() { }, () -> loadPublicCinemaDetail(slug));
    }

    @Transactional(readOnly = true)
    @Override
    public List<String> findPublicCities() {
        return publicCinemaCache.getOrLoad(
                "cities",
                new TypeReference<List<String>>() { },
                () -> cinemaRepository.findDistinctCitiesByStatus(CinemaStatus.ACTIVE));
    }

    @Transactional(readOnly = true)
    @Override
    public List<SeatTypeResponse> findSeatTypes() {
        return seatTypeRepository.findAllByOrderByIdAsc().stream().map(CinemaMapper::toSeatTypeResponse).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public SeatLayoutResponse findPublicSeatLayout(Long hallId) {
        Hall hall = hallRepository.findById(hallId)
                .filter(foundHall -> foundHall.getStatus() == HallStatus.ACTIVE)
                .orElseThrow(CinemaAccessGuard::hallNotFound);
        boolean cinemaIsActive = cinemaRepository.findById(hall.getCinemaId())
                .map(cinema -> cinema.getStatus() == CinemaStatus.ACTIVE)
                .orElse(false);
        if (!cinemaIsActive) {
            throw CinemaAccessGuard.hallNotFound();
        }
        return CinemaMapper.toSeatLayoutResponse(
                hall, seatRepository.findAllByHallIdOrderBySeatRowAscSeatNumberAsc(hallId));
    }

    private List<CinemaResponse> loadPublicCinemas(String city) {
        List<Cinema> cinemas = cinemaRepository.findAll(
                CinemaSpecifications.search(null, city, CinemaStatus.ACTIVE, null), Sort.by("city", "name", "id"));
        if (cinemas.isEmpty()) {
            return List.of();
        }
        List<Long> cinemaIds = cinemas.stream().map(Cinema::getId).toList();
        Map<Long, Cinema> cinemasWithFacilities = cinemaRepository.findWithFacilitiesByIdIn(cinemaIds).stream()
                .collect(Collectors.toMap(Cinema::getId, Function.identity()));
        Map<Long, Long> activeHallCounts = hallRepository.findByCinemaIdInOrderByNameAsc(cinemaIds).stream()
                .filter(hall -> hall.getStatus() == HallStatus.ACTIVE)
                .collect(Collectors.groupingBy(Hall::getCinemaId, Collectors.counting()));
        return cinemaIds.stream()
                .map(cinemaId -> CinemaMapper.toCinemaResponse(
                        cinemasWithFacilities.get(cinemaId), activeHallCounts.getOrDefault(cinemaId, 0L).intValue()))
                .toList();
    }

    private CinemaDetailResponse loadPublicCinemaDetail(String slug) {
        Cinema cinema = cinemaRepository.findWithFacilitiesBySlugAndStatus(slug, CinemaStatus.ACTIVE)
                .orElseThrow(() -> new AppException("Không tìm thấy cụm rạp", HttpStatus.NOT_FOUND, "CINEMA_NOT_FOUND"));
        List<Hall> activeHalls = hallRepository.findByCinemaIdAndStatusOrderByNameAsc(cinema.getId(), HallStatus.ACTIVE);
        return CinemaMapper.toCinemaDetailResponse(cinema, activeHalls);
    }
}
