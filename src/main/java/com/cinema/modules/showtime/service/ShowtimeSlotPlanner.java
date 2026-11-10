package com.cinema.modules.showtime.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import com.cinema.modules.showtime.dto.response.ShowtimeConflictResponse;
import com.cinema.modules.showtime.exception.ShowtimeConflictException;
import com.cinema.modules.showtime.model.PlannedShowtimeSlot;
import com.cinema.modules.showtime.util.ShowtimeRules;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class ShowtimeSlotPlanner {

    private final ICinemaQueryService cinemaQueryService;
    private final IMovieQueryService movieQueryService;
    private final IBranchAccessService branchAccessService;
    private final ShowtimeConflictDetector showtimeConflictDetector;

    public ShowtimeSlotPlanner(
            ICinemaQueryService cinemaQueryService,
            IMovieQueryService movieQueryService,
            IBranchAccessService branchAccessService,
            ShowtimeConflictDetector showtimeConflictDetector) {
        this.cinemaQueryService = cinemaQueryService;
        this.movieQueryService = movieQueryService;
        this.branchAccessService = branchAccessService;
        this.showtimeConflictDetector = showtimeConflictDetector;
    }

    public PlannedShowtimeSlot plan(
            Long actorUserId,
            Long movieId,
            Long hallId,
            Instant requestedStartTime,
            Instant now,
            Long excludedShowtimeId) {
        HallInfoResponse hallInfoResponse = cinemaQueryService.findHallInfos(List.of(hallId)).get(hallId);
        if (hallInfoResponse == null || hallInfoResponse.deleted()) {
            throw new AppException("Không tìm thấy phòng chiếu", HttpStatus.NOT_FOUND, "HALL_NOT_FOUND");
        }
        branchAccessService.assertCanAccessCinema(actorUserId, hallInfoResponse.cinemaId());
        if (cinemaQueryService.findActiveCinemaIds(List.of(hallInfoResponse.cinemaId())).isEmpty()) {
            throw new AppException(
                    "Cụm rạp đang ngừng hoạt động, không thể xếp lịch chiếu", HttpStatus.CONFLICT, "CINEMA_NOT_ACTIVE");
        }
        if (hallInfoResponse.status() != HallStatus.ACTIVE) {
            throw new AppException(
                    "Phòng chiếu đang bảo trì, không thể xếp lịch chiếu", HttpStatus.CONFLICT, "HALL_NOT_ACTIVE");
        }
        MovieInfoResponse movieInfoResponse = movieQueryService.findMovieInfos(List.of(movieId)).get(movieId);
        if (movieInfoResponse == null || movieInfoResponse.deleted()) {
            throw new AppException("Không tìm thấy phim", HttpStatus.NOT_FOUND, "MOVIE_NOT_FOUND");
        }
        if (movieInfoResponse.status() == MovieStatus.ENDED) {
            throw new AppException("Phim đã ngừng chiếu, không thể xếp lịch chiếu", HttpStatus.CONFLICT, "MOVIE_ENDED");
        }
        Instant startTime = requestedStartTime.truncatedTo(ChronoUnit.MINUTES);
        if (!startTime.isAfter(now)) {
            throw new AppException(
                    "Giờ bắt đầu phải nằm trong tương lai", HttpStatus.BAD_REQUEST, "SHOWTIME_START_IN_PAST");
        }
        Instant endTime = ShowtimeRules.calculateEndTime(startTime, movieInfoResponse.durationMinutes());
        List<ShowtimeConflictResponse> conflicts =
                showtimeConflictDetector.findConflicts(hallId, startTime, endTime, excludedShowtimeId);
        if (!conflicts.isEmpty()) {
            throw new ShowtimeConflictException(conflicts);
        }
        return new PlannedShowtimeSlot(startTime, endTime);
    }
}
