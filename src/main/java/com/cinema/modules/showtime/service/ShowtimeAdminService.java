package com.cinema.modules.showtime.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.util.BusinessZone;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import com.cinema.modules.showtime.dto.request.ShowtimeRequest;
import com.cinema.modules.showtime.dto.response.ShowtimeConflictResponse;
import com.cinema.modules.showtime.dto.response.ShowtimeResponse;
import com.cinema.modules.showtime.entity.Showtime;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import com.cinema.modules.showtime.exception.ShowtimeConflictException;
import com.cinema.modules.showtime.repository.ShowtimeRepository;
import com.cinema.modules.showtime.service.interfaces.IShowtimeAdminService;
import com.cinema.modules.showtime.specification.ShowtimeSpecifications;
import com.cinema.modules.showtime.util.ShowtimeRules;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Service
public class ShowtimeAdminService implements IShowtimeAdminService {

    private static final Sort SHOWTIME_SORT = Sort.by("startTime", "id");

    private final ShowtimeRepository showtimeRepository;
    private final ShowtimeConflictDetector showtimeConflictDetector;
    private final ShowtimeBookingChecker showtimeBookingChecker;
    private final ShowtimeViewAssembler showtimeViewAssembler;
    private final ICinemaQueryService cinemaQueryService;
    private final IMovieQueryService movieQueryService;
    private final IBranchAccessService branchAccessService;
    private final Clock clock;

    public ShowtimeAdminService(
            ShowtimeRepository showtimeRepository,
            ShowtimeConflictDetector showtimeConflictDetector,
            ShowtimeBookingChecker showtimeBookingChecker,
            ShowtimeViewAssembler showtimeViewAssembler,
            ICinemaQueryService cinemaQueryService,
            IMovieQueryService movieQueryService,
            IBranchAccessService branchAccessService,
            Clock clock) {
        this.showtimeRepository = showtimeRepository;
        this.showtimeConflictDetector = showtimeConflictDetector;
        this.showtimeBookingChecker = showtimeBookingChecker;
        this.showtimeViewAssembler = showtimeViewAssembler;
        this.cinemaQueryService = cinemaQueryService;
        this.movieQueryService = movieQueryService;
        this.branchAccessService = branchAccessService;
        this.clock = clock;
    }

    @Override
    public List<ShowtimeResponse> listShowtimes(
            Long actorUserId, Long cinemaId, LocalDate date, Long movieId, ShowtimeStatus showtimeStatus) {
        branchAccessService.assertCanAccessCinema(actorUserId, cinemaId);
        if (!cinemaQueryService.findAllCinemaIds().contains(cinemaId)) {
            throw new AppException("Không tìm thấy cụm rạp", HttpStatus.NOT_FOUND, "CINEMA_NOT_FOUND");
        }
        Set<Long> hallIds = cinemaQueryService.findHallIdsByCinemaIds(List.of(cinemaId));
        if (hallIds.isEmpty()) {
            return List.of();
        }
        Specification<Showtime> specification = Specification.where(ShowtimeSpecifications.inHalls(hallIds))
                .and(ShowtimeSpecifications.overlapping(BusinessZone.startOfDay(date), BusinessZone.startOfNextDay(date)))
                .and(ShowtimeSpecifications.withMovie(movieId))
                .and(ShowtimeSpecifications.withStatus(showtimeStatus));
        return showtimeViewAssembler.toShowtimeResponses(
                showtimeRepository.findAll(specification, SHOWTIME_SORT), Instant.now(clock));
    }

    @Override
    public ShowtimeResponse getShowtime(Long actorUserId, Long showtimeId) {
        Showtime showtime = loadShowtime(showtimeId);
        assertCanAccessShowtime(actorUserId, showtime);
        return showtimeViewAssembler.toShowtimeResponses(List.of(showtime), Instant.now(clock)).get(0);
    }

    @Override
    public ShowtimeResponse createShowtime(Long actorUserId, ShowtimeRequest showtimeRequest) {
        Instant now = Instant.now(clock);
        ShowtimeSlot showtimeSlot = prepareSlot(actorUserId, showtimeRequest, now, null);
        Showtime showtime = new Showtime();
        applySlot(showtime, showtimeRequest, showtimeSlot);
        Showtime savedShowtime = saveWithoutOverlap(showtime, showtimeSlot, null);
        return showtimeViewAssembler.toShowtimeResponses(List.of(savedShowtime), now).get(0);
    }

    @Override
    public ShowtimeResponse updateShowtime(Long actorUserId, Long showtimeId, ShowtimeRequest showtimeRequest) {
        Instant now = Instant.now(clock);
        Showtime showtime = loadShowtime(showtimeId);
        assertCanAccessShowtime(actorUserId, showtime);
        assertActive(showtime);
        if (!showtime.getStartTime().isAfter(now)) {
            throw new AppException("Suất chiếu đã bắt đầu, không thể chỉnh sửa", HttpStatus.CONFLICT, "SHOWTIME_STARTED");
        }
        assertNoBookings(showtime, now, "Suất chiếu đã có vé hoặc ghế đang được giữ, không thể chỉnh sửa");
        ShowtimeSlot showtimeSlot = prepareSlot(actorUserId, showtimeRequest, now, showtimeId);
        applySlot(showtime, showtimeRequest, showtimeSlot);
        Showtime savedShowtime = saveWithoutOverlap(showtime, showtimeSlot, showtimeId);
        return showtimeViewAssembler.toShowtimeResponses(List.of(savedShowtime), now).get(0);
    }

    @Override
    public ShowtimeResponse cancelShowtime(Long actorUserId, Long showtimeId) {
        Instant now = Instant.now(clock);
        Showtime showtime = loadShowtime(showtimeId);
        assertCanAccessShowtime(actorUserId, showtime);
        assertActive(showtime);
        if (!showtime.getEndTime().isAfter(now)) {
            throw new AppException("Suất chiếu đã kết thúc, không thể hủy", HttpStatus.CONFLICT, "SHOWTIME_ENDED");
        }
        assertNoBookings(showtime, now, "Suất chiếu đã có vé hoặc ghế đang được giữ, hãy hoàn vé trước khi hủy suất");
        showtime.setStatus(ShowtimeStatus.CANCELLED);
        Showtime savedShowtime = showtimeRepository.saveAndFlush(showtime);
        return showtimeViewAssembler.toShowtimeResponses(List.of(savedShowtime), now).get(0);
    }

    @Override
    public List<MovieInfoResponse> listSchedulableMovies() {
        return movieQueryService.findSchedulableMovieInfos();
    }

    private ShowtimeSlot prepareSlot(Long actorUserId, ShowtimeRequest showtimeRequest, Instant now, Long excludedShowtimeId) {
        HallInfoResponse hallInfoResponse = cinemaQueryService
                .findHallInfos(List.of(showtimeRequest.hallId()))
                .get(showtimeRequest.hallId());
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
        MovieInfoResponse movieInfoResponse = movieQueryService
                .findMovieInfos(List.of(showtimeRequest.movieId()))
                .get(showtimeRequest.movieId());
        if (movieInfoResponse == null || movieInfoResponse.deleted()) {
            throw new AppException("Không tìm thấy phim", HttpStatus.NOT_FOUND, "MOVIE_NOT_FOUND");
        }
        if (movieInfoResponse.status() == MovieStatus.ENDED) {
            throw new AppException("Phim đã ngừng chiếu, không thể xếp lịch chiếu", HttpStatus.CONFLICT, "MOVIE_ENDED");
        }
        Instant startTime = showtimeRequest.startTime().truncatedTo(ChronoUnit.MINUTES);
        if (!startTime.isAfter(now)) {
            throw new AppException(
                    "Giờ bắt đầu phải nằm trong tương lai", HttpStatus.BAD_REQUEST, "SHOWTIME_START_IN_PAST");
        }
        Instant endTime = ShowtimeRules.calculateEndTime(startTime, movieInfoResponse.durationMinutes());
        List<ShowtimeConflictResponse> conflicts =
                showtimeConflictDetector.findConflicts(hallInfoResponse.id(), startTime, endTime, excludedShowtimeId);
        if (!conflicts.isEmpty()) {
            throw new ShowtimeConflictException(conflicts);
        }
        return new ShowtimeSlot(startTime, endTime);
    }

    private void applySlot(Showtime showtime, ShowtimeRequest showtimeRequest, ShowtimeSlot showtimeSlot) {
        showtime.setMovieId(showtimeRequest.movieId());
        showtime.setHallId(showtimeRequest.hallId());
        showtime.setStartTime(showtimeSlot.startTime());
        showtime.setEndTime(showtimeSlot.endTime());
        showtime.setBasePrice(showtimeRequest.basePrice());
        showtime.setStatus(ShowtimeStatus.ACTIVE);
    }

    private Showtime saveWithoutOverlap(Showtime showtime, ShowtimeSlot showtimeSlot, Long excludedShowtimeId) {
        try {
            return showtimeRepository.saveAndFlush(showtime);
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            if (!showtimeConflictDetector.isOverlapViolation(dataIntegrityViolationException)) {
                throw dataIntegrityViolationException;
            }
            throw new ShowtimeConflictException(showtimeConflictDetector.findConflicts(
                    showtime.getHallId(), showtimeSlot.startTime(), showtimeSlot.endTime(), excludedShowtimeId));
        }
    }

    private Showtime loadShowtime(Long showtimeId) {
        return showtimeRepository.findById(showtimeId).orElseThrow(
                () -> new AppException("Không tìm thấy suất chiếu", HttpStatus.NOT_FOUND, "SHOWTIME_NOT_FOUND"));
    }

    private void assertCanAccessShowtime(Long actorUserId, Showtime showtime) {
        HallInfoResponse hallInfoResponse =
                cinemaQueryService.findHallInfos(List.of(showtime.getHallId())).get(showtime.getHallId());
        branchAccessService.assertCanAccessCinema(actorUserId, hallInfoResponse.cinemaId());
    }

    private void assertActive(Showtime showtime) {
        if (showtime.getStatus() != ShowtimeStatus.ACTIVE) {
            throw new AppException("Suất chiếu đã bị hủy", HttpStatus.CONFLICT, "SHOWTIME_CANCELLED");
        }
    }

    private void assertNoBookings(Showtime showtime, Instant now, String message) {
        if (!showtimeBookingChecker.findShowtimeIdsWithBookings(List.of(showtime.getId()), now).isEmpty()) {
            throw new AppException(message, HttpStatus.CONFLICT, "SHOWTIME_HAS_BOOKINGS");
        }
    }

    private record ShowtimeSlot(Instant startTime, Instant endTime) {
    }
}
