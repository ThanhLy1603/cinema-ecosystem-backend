package com.cinema.modules.cinema.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.cinema.dto.request.HallRequest;
import com.cinema.modules.cinema.dto.response.HallResponse;
import com.cinema.modules.cinema.entity.Hall;
import com.cinema.modules.cinema.repository.HallRepository;
import com.cinema.modules.cinema.service.interfaces.IHallService;
import com.cinema.modules.cinema.util.CinemaMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class HallService implements IHallService {

    private final HallRepository hallRepository;
    private final CinemaAccessGuard cinemaAccessGuard;
    private final HallUsageChecker hallUsageChecker;
    private final PublicCinemaCache publicCinemaCache;
    private final Clock clock;

    public HallService(
            HallRepository hallRepository,
            CinemaAccessGuard cinemaAccessGuard,
            HallUsageChecker hallUsageChecker,
            PublicCinemaCache publicCinemaCache,
            Clock clock) {
        this.clock = clock;
        this.hallRepository = hallRepository;
        this.cinemaAccessGuard = cinemaAccessGuard;
        this.hallUsageChecker = hallUsageChecker;
        this.publicCinemaCache = publicCinemaCache;
    }

    @Transactional(readOnly = true)
    @Override
    public List<HallResponse> listHalls(Long actorUserId, Long cinemaId, boolean deleted) {
        cinemaAccessGuard.requireAccessibleCinema(actorUserId, cinemaId);
        return hallRepository.findByCinemaIdAndDeletedOrderByNameAsc(cinemaId, deleted).stream()
                .map(CinemaMapper::toHallResponse)
                .toList();
    }

    @Transactional
    @Override
    public HallResponse createHall(Long actorUserId, Long cinemaId, HallRequest hallRequest) {
        cinemaAccessGuard.requireAccessibleCinema(actorUserId, cinemaId);
        String name = hallRequest.name().trim();
        if (hallRepository.existsByCinemaIdAndNameIgnoreCaseAndDeletedFalse(cinemaId, name)) {
            throw nameAlreadyUsed();
        }
        Hall hall = new Hall();
        hall.setCinemaId(cinemaId);
        applyRequest(hall, hallRequest);
        return saveAndEvict(hall);
    }

    @Transactional
    @Override
    public HallResponse updateHall(Long actorUserId, Long hallId, HallRequest hallRequest) {
        Hall hall = cinemaAccessGuard.requireAccessibleHall(actorUserId, hallId);
        String name = hallRequest.name().trim();
        if (hallRepository.existsByCinemaIdAndNameIgnoreCaseAndIdNotAndDeletedFalse(hall.getCinemaId(), name, hallId)) {
            throw nameAlreadyUsed();
        }
        applyRequest(hall, hallRequest);
        return saveAndEvict(hall);
    }

    @Transactional
    @Override
    public void deleteHall(Long actorUserId, Long hallId) {
        Hall hall = cinemaAccessGuard.requireAccessibleHall(actorUserId, hallId);
        Instant now = Instant.now(clock);
        if (hallUsageChecker.countUpcomingShowtimesByHall(hallId, now) > 0) {
            throw new AppException(
                    "Phòng chiếu còn suất chiếu chưa diễn ra, hãy hủy suất chiếu hoặc chuyển sang bảo trì",
                    HttpStatus.CONFLICT,
                    "HALL_HAS_UPCOMING_SHOWTIMES");
        }
        hall.markDeleted(now);
        hallRepository.saveAndFlush(hall);
        publicCinemaCache.evictAfterCommit();
    }

    @Transactional
    @Override
    public HallResponse restoreHall(Long actorUserId, Long hallId) {
        Hall hall = cinemaAccessGuard.requireDeletedHallOfActiveCinema(actorUserId, hallId);
        if (hallRepository.existsByCinemaIdAndNameIgnoreCaseAndDeletedFalse(hall.getCinemaId(), hall.getName())) {
            throw nameAlreadyUsed();
        }
        hall.restore();
        return saveAndEvict(hall);
    }

    private void applyRequest(Hall hall, HallRequest hallRequest) {
        hall.setName(hallRequest.name().trim());
        hall.setScreenType(hallRequest.screenType());
        hall.setStatus(hallRequest.status());
    }

    private HallResponse saveAndEvict(Hall hall) {
        try {
            Hall savedHall = hallRepository.saveAndFlush(hall);
            publicCinemaCache.evictAfterCommit();
            return CinemaMapper.toHallResponse(savedHall);
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            throw nameAlreadyUsed();
        }
    }

    private static AppException nameAlreadyUsed() {
        return new AppException(
                "Tên phòng chiếu đã tồn tại trong cụm rạp này", HttpStatus.CONFLICT, "HALL_NAME_EXISTS");
    }
}
