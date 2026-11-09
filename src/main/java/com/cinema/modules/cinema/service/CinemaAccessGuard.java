package com.cinema.modules.cinema.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.cinema.entity.Cinema;
import com.cinema.modules.cinema.entity.Hall;
import com.cinema.modules.cinema.repository.CinemaRepository;
import com.cinema.modules.cinema.repository.HallRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CinemaAccessGuard {

    private final CinemaRepository cinemaRepository;
    private final HallRepository hallRepository;
    private final IBranchAccessService branchAccessService;

    public CinemaAccessGuard(
            CinemaRepository cinemaRepository,
            HallRepository hallRepository,
            IBranchAccessService branchAccessService) {
        this.cinemaRepository = cinemaRepository;
        this.hallRepository = hallRepository;
        this.branchAccessService = branchAccessService;
    }

    public Cinema requireAccessibleCinema(Long actorUserId, Long cinemaId) {
        Cinema cinema = cinemaRepository.findByIdAndDeleted(cinemaId, false).orElseThrow(CinemaAccessGuard::cinemaNotFound);
        branchAccessService.assertCanAccessCinema(actorUserId, cinema.getId());
        return cinema;
    }

    public Hall requireAccessibleHall(Long actorUserId, Long hallId) {
        Hall hall = hallRepository.findByIdAndDeleted(hallId, false).orElseThrow(CinemaAccessGuard::hallNotFound);
        requireAccessibleCinema(actorUserId, hall.getCinemaId());
        return hall;
    }

    public Hall requireDeletedHallOfActiveCinema(Long actorUserId, Long hallId) {
        Hall hall = hallRepository.findByIdAndDeleted(hallId, true).orElseThrow(CinemaAccessGuard::hallNotFound);
        branchAccessService.assertCanAccessCinema(actorUserId, hall.getCinemaId());
        if (cinemaRepository.findByIdAndDeleted(hall.getCinemaId(), false).isEmpty()) {
            throw new AppException(
                    "Cụm rạp của phòng chiếu này đã bị xóa, hãy khôi phục cụm rạp trước",
                    HttpStatus.CONFLICT,
                    "CINEMA_DELETED");
        }
        return hall;
    }

    public boolean isGlobalAdmin(Long actorUserId) {
        return branchAccessService.hasAccessToAllCinemas(actorUserId);
    }

    public static AppException cinemaNotFound() {
        return new AppException("Không tìm thấy cụm rạp", HttpStatus.NOT_FOUND, "CINEMA_NOT_FOUND");
    }

    public static AppException hallNotFound() {
        return new AppException("Không tìm thấy phòng chiếu", HttpStatus.NOT_FOUND, "HALL_NOT_FOUND");
    }
}
