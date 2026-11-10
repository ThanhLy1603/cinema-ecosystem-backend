package com.cinema.modules.cinema.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.cinema.dto.request.CinemaRequest;
import com.cinema.modules.cinema.dto.response.CinemaDetailResponse;
import com.cinema.modules.cinema.dto.response.CinemaResponse;
import com.cinema.modules.cinema.entity.Cinema;
import com.cinema.modules.cinema.entity.CinemaFacility;
import com.cinema.modules.cinema.entity.Hall;
import com.cinema.modules.cinema.enums.CinemaStatus;
import com.cinema.modules.cinema.repository.CinemaFacilityRepository;
import com.cinema.modules.cinema.repository.CinemaRepository;
import com.cinema.modules.cinema.repository.HallRepository;
import com.cinema.modules.cinema.service.interfaces.ICinemaAdminService;
import com.cinema.modules.cinema.specification.CinemaSpecifications;
import com.cinema.modules.cinema.util.CinemaMapper;
import com.cinema.common.util.SlugGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CinemaAdminService implements ICinemaAdminService {

    static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_SLUG_ATTEMPTS = 50;

    private final CinemaRepository cinemaRepository;
    private final CinemaFacilityRepository cinemaFacilityRepository;
    private final HallRepository hallRepository;
    private final IBranchAccessService branchAccessService;
    private final CinemaAccessGuard cinemaAccessGuard;
    private final HallUsageChecker hallUsageChecker;
    private final PublicCinemaCache publicCinemaCache;
    private final Clock clock;

    public CinemaAdminService(
            CinemaRepository cinemaRepository,
            CinemaFacilityRepository cinemaFacilityRepository,
            HallRepository hallRepository,
            IBranchAccessService branchAccessService,
            CinemaAccessGuard cinemaAccessGuard,
            HallUsageChecker hallUsageChecker,
            PublicCinemaCache publicCinemaCache,
            Clock clock) {
        this.cinemaRepository = cinemaRepository;
        this.cinemaFacilityRepository = cinemaFacilityRepository;
        this.hallRepository = hallRepository;
        this.branchAccessService = branchAccessService;
        this.cinemaAccessGuard = cinemaAccessGuard;
        this.hallUsageChecker = hallUsageChecker;
        this.publicCinemaCache = publicCinemaCache;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<CinemaResponse> searchCinemas(
            Long actorUserId, String keyword, String city, CinemaStatus status, boolean deleted, int page, int size) {
        Set<Long> accessibleCinemaIds = branchAccessService.hasAccessToAllCinemas(actorUserId)
                ? null
                : branchAccessService.getAccessibleCinemaIds(actorUserId);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE),
                Sort.by("name", "id"));
        Page<Cinema> cinemaPage = cinemaRepository.findAll(
                CinemaSpecifications.search(keyword, city, status, accessibleCinemaIds, deleted), pageable);
        List<Long> cinemaIds = cinemaPage.getContent().stream().map(Cinema::getId).toList();
        Map<Long, Cinema> cinemasWithFacilities = cinemaRepository.findWithFacilitiesByIdIn(cinemaIds).stream()
                .collect(Collectors.toMap(Cinema::getId, Function.identity()));
        Map<Long, Long> hallCounts = hallRepository.findByCinemaIdInAndDeletedFalseOrderByNameAsc(cinemaIds).stream()
                .collect(Collectors.groupingBy(Hall::getCinemaId, Collectors.counting()));
        List<CinemaResponse> cinemaResponses = cinemaIds.stream()
                .map(cinemaId -> CinemaMapper.toCinemaResponse(
                        cinemasWithFacilities.get(cinemaId), hallCounts.getOrDefault(cinemaId, 0L).intValue()))
                .toList();
        return PageResponse.of(cinemaPage, cinemaResponses);
    }

    @Transactional(readOnly = true)
    @Override
    public CinemaDetailResponse getCinema(Long actorUserId, Long cinemaId) {
        cinemaAccessGuard.requireAccessibleCinema(actorUserId, cinemaId);
        return toDetailResponse(loadCinemaWithFacilities(cinemaId));
    }

    @Transactional
    @Override
    public CinemaDetailResponse createCinema(CinemaRequest cinemaRequest) {
        validateRequestShape(cinemaRequest);
        Cinema cinema = new Cinema();
        applyRequest(cinema, cinemaRequest);
        cinema.setSlug(generateUniqueSlug(cinemaRequest.name()));
        Cinema savedCinema = saveCinema(cinema);
        publicCinemaCache.evictAfterCommit();
        return toDetailResponse(savedCinema);
    }

    @Transactional
    @Override
    public CinemaDetailResponse updateCinema(Long actorUserId, Long cinemaId, CinemaRequest cinemaRequest) {
        cinemaAccessGuard.requireAccessibleCinema(actorUserId, cinemaId);
        validateRequestShape(cinemaRequest);
        Cinema cinema = loadCinemaWithFacilities(cinemaId);
        if (cinemaRequest.status() != cinema.getStatus() && !cinemaAccessGuard.isGlobalAdmin(actorUserId)) {
            throw new AppException(
                    "Chỉ quản trị viên mới được đổi trạng thái cụm rạp", HttpStatus.FORBIDDEN, "ADMIN_ONLY");
        }
        applyRequest(cinema, cinemaRequest);
        Cinema savedCinema = saveCinema(cinema);
        publicCinemaCache.evictAfterCommit();
        return toDetailResponse(savedCinema);
    }

    @Transactional
    @Override
    public void deleteCinema(Long cinemaId) {
        Cinema cinema = cinemaRepository.findByIdAndDeleted(cinemaId, false)
                .orElseThrow(CinemaAccessGuard::cinemaNotFound);
        Instant now = Instant.now(clock);
        if (hallUsageChecker.countUpcomingShowtimesByCinema(cinemaId, now) > 0) {
            throw new AppException(
                    "Cụm rạp còn suất chiếu chưa diễn ra, hãy hủy suất chiếu hoặc chuyển rạp sang tạm ngưng",
                    HttpStatus.CONFLICT,
                    "CINEMA_HAS_UPCOMING_SHOWTIMES");
        }
        cinema.markDeleted(now);
        cinemaRepository.save(cinema);
        publicCinemaCache.evictAfterCommit();
    }

    @Transactional
    @Override
    public CinemaDetailResponse restoreCinema(Long cinemaId) {
        Cinema cinema = cinemaRepository.findWithFacilitiesByIdAndDeleted(cinemaId, true)
                .orElseThrow(() -> new AppException(
                        "Không tìm thấy cụm rạp đã xóa", HttpStatus.NOT_FOUND, "CINEMA_NOT_FOUND"));
        if (cinemaRepository.existsBySlugAndDeletedFalse(cinema.getSlug())) {
            cinema.setSlug(generateUniqueSlug(cinema.getName()));
        }
        cinema.restore();
        Cinema savedCinema = saveCinema(cinema);
        publicCinemaCache.evictAfterCommit();
        return toDetailResponse(savedCinema);
    }

    private Cinema loadCinemaWithFacilities(Long cinemaId) {
        return cinemaRepository.findWithFacilitiesByIdAndDeleted(cinemaId, false)
                .orElseThrow(CinemaAccessGuard::cinemaNotFound);
    }

    private CinemaDetailResponse toDetailResponse(Cinema cinema) {
        return CinemaMapper.toCinemaDetailResponse(cinema, hallRepository.findByCinemaIdAndDeletedOrderByNameAsc(cinema.getId(), false));
    }

    private void validateRequestShape(CinemaRequest cinemaRequest) {
        if ((cinemaRequest.latitude() == null) != (cinemaRequest.longitude() == null)) {
            throw new AppException(
                    "Vĩ độ và kinh độ phải được nhập cùng nhau", HttpStatus.BAD_REQUEST, "INVALID_COORDINATES");
        }
    }

    private void applyRequest(Cinema cinema, CinemaRequest cinemaRequest) {
        cinema.setName(cinemaRequest.name().trim());
        cinema.setCity(cinemaRequest.city().trim());
        cinema.setAddress(cinemaRequest.address().trim());
        cinema.setPhone(cinemaRequest.phone() == null || cinemaRequest.phone().isBlank()
                ? null
                : cinemaRequest.phone().trim());
        cinema.setLatitude(cinemaRequest.latitude());
        cinema.setLongitude(cinemaRequest.longitude());
        cinema.setStatus(cinemaRequest.status());
        cinema.setFacilities(resolveFacilities(cinemaRequest.facilityIds()));
    }

    private Set<CinemaFacility> resolveFacilities(List<Long> facilityIds) {
        if (facilityIds == null || facilityIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Long> distinctFacilityIds = new HashSet<>(facilityIds);
        List<CinemaFacility> cinemaFacilities = cinemaFacilityRepository.findAllByIdInAndDeletedFalse(distinctFacilityIds);
        if (cinemaFacilities.size() != distinctFacilityIds.size()) {
            throw new AppException("Có tiện ích không tồn tại", HttpStatus.BAD_REQUEST, "INVALID_FACILITY");
        }
        return new HashSet<>(cinemaFacilities);
    }

    private String generateUniqueSlug(String name) {
        String baseSlug = SlugGenerator.toSlug(name);
        String slug = baseSlug;
        for (int attempt = 2; cinemaRepository.existsBySlugAndDeletedFalse(slug); attempt++) {
            if (attempt > MAX_SLUG_ATTEMPTS) {
                throw new AppException("Không tạo được đường dẫn cho cụm rạp", HttpStatus.CONFLICT, "SLUG_EXHAUSTED");
            }
            slug = baseSlug + "-" + attempt;
        }
        return slug;
    }

    private Cinema saveCinema(Cinema cinema) {
        try {
            return cinemaRepository.saveAndFlush(cinema);
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            throw new AppException(
                    "Cụm rạp đã tồn tại hoặc dữ liệu không hợp lệ", HttpStatus.CONFLICT, "CINEMA_CONFLICT");
        }
    }
}
