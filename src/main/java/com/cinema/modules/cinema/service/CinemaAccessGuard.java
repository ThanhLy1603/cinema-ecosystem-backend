package com.cinema.modules.cinema.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.cinema.entity.Cinema;
import com.cinema.modules.cinema.entity.Hall;
import com.cinema.modules.cinema.enums.CinemaStatus;
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
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .filter(foundCinema -> foundCinema.getStatus() != CinemaStatus.DELETED)
                .orElseThrow(CinemaAccessGuard::cinemaNotFound);
        branchAccessService.assertCanAccessCinema(actorUserId, cinema.getId());
        return cinema;
    }

    public Hall requireAccessibleHall(Long actorUserId, Long hallId) {
        Hall hall = hallRepository.findById(hallId).orElseThrow(CinemaAccessGuard::hallNotFound);
        requireAccessibleCinema(actorUserId, hall.getCinemaId());
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
