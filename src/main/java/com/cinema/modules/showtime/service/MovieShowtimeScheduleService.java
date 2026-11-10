package com.cinema.modules.showtime.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.util.BusinessZone;
import com.cinema.modules.cinema.dto.response.CinemaOptionResponse;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import com.cinema.modules.showtime.dto.response.BookableShowtimeResponse;
import com.cinema.modules.showtime.dto.response.CinemaShowtimesResponse;
import com.cinema.modules.showtime.dto.response.MovieShowtimeDatesResponse;
import com.cinema.modules.showtime.entity.Showtime;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import com.cinema.modules.showtime.repository.ShowtimeRepository;
import com.cinema.modules.showtime.service.interfaces.IMovieShowtimeScheduleService;
import com.cinema.modules.showtime.specification.ShowtimeSpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MovieShowtimeScheduleService implements IMovieShowtimeScheduleService {

    private static final Sort SHOWTIME_SORT = Sort.by("startTime", "id");

    private final ShowtimeRepository showtimeRepository;
    private final ShowtimeSeatOccupancyReader showtimeSeatOccupancyReader;
    private final ICinemaQueryService cinemaQueryService;
    private final IMovieQueryService movieQueryService;
    private final Clock clock;

    public MovieShowtimeScheduleService(
            ShowtimeRepository showtimeRepository,
            ShowtimeSeatOccupancyReader showtimeSeatOccupancyReader,
            ICinemaQueryService cinemaQueryService,
            IMovieQueryService movieQueryService,
            Clock clock) {
        this.showtimeRepository = showtimeRepository;
        this.showtimeSeatOccupancyReader = showtimeSeatOccupancyReader;
        this.cinemaQueryService = cinemaQueryService;
        this.movieQueryService = movieQueryService;
        this.clock = clock;
    }

    @Override
    public MovieShowtimeDatesResponse findShowtimeDates(Long movieId, YearMonth yearMonth, Long cinemaId) {
        requireVisibleMovie(movieId);
        Instant now = Instant.now(clock);
        Instant monthStart = BusinessZone.startOfDay(yearMonth.atDay(1));
        Instant windowFrom = monthStart.isBefore(now) ? now : monthStart;
        Instant windowTo = BusinessZone.startOfNextDay(yearMonth.atEndOfMonth());
        Map<Long, HallInfoResponse> hallInfoResponses = findBookableHalls(resolveCinemas(cinemaId));
        if (hallInfoResponses.isEmpty() || !windowFrom.isBefore(windowTo)) {
            return new MovieShowtimeDatesResponse(movieId, List.of());
        }
        List<Showtime> showtimes = findShowtimes(movieId, hallInfoResponses.keySet(), windowFrom, windowTo);
        List<LocalDate> dates = showtimes.stream()
                .map(showtime -> BusinessZone.toLocalDate(showtime.getStartTime()))
                .distinct()
                .sorted()
                .toList();
        return new MovieShowtimeDatesResponse(movieId, dates);
    }

    @Override
    public List<CinemaShowtimesResponse> findShowtimesByDate(Long movieId, LocalDate date, Long cinemaId) {
        requireVisibleMovie(movieId);
        Instant now = Instant.now(clock);
        Instant dayStart = BusinessZone.startOfDay(date);
        Instant windowFrom = dayStart.isBefore(now) ? now : dayStart;
        Instant windowTo = BusinessZone.startOfNextDay(date);
        List<CinemaOptionResponse> cinemaOptionResponses = resolveCinemas(cinemaId);
        Map<Long, HallInfoResponse> hallInfoResponses = findBookableHalls(cinemaOptionResponses);
        if (hallInfoResponses.isEmpty() || !windowFrom.isBefore(windowTo)) {
            return List.of();
        }
        List<Showtime> showtimes = findShowtimes(movieId, hallInfoResponses.keySet(), windowFrom, windowTo);
        if (showtimes.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> totalSeatCounts = cinemaQueryService.countBookableSeatsByHall(hallInfoResponses.keySet());
        Map<Long, Integer> occupiedSeatCounts = showtimeSeatOccupancyReader.countOccupiedSeatsByShowtime(
                showtimes.stream().map(Showtime::getId).collect(Collectors.toSet()), now);
        List<CinemaShowtimesResponse> cinemaShowtimesResponses = new ArrayList<>();
        for (CinemaOptionResponse cinemaOptionResponse : cinemaOptionResponses) {
            List<BookableShowtimeResponse> bookableShowtimeResponses = showtimes.stream()
                    .filter(showtime -> hallInfoResponses.get(showtime.getHallId()).cinemaId()
                            .equals(cinemaOptionResponse.id()))
                    .map(showtime -> toBookableShowtime(
                            showtime,
                            hallInfoResponses.get(showtime.getHallId()),
                            totalSeatCounts.getOrDefault(showtime.getHallId(), 0),
                            occupiedSeatCounts.getOrDefault(showtime.getId(), 0)))
                    .toList();
            if (!bookableShowtimeResponses.isEmpty()) {
                cinemaShowtimesResponses.add(new CinemaShowtimesResponse(
                        cinemaOptionResponse.id(), cinemaOptionResponse.name(), bookableShowtimeResponses));
            }
        }
        return cinemaShowtimesResponses;
    }

    private void requireVisibleMovie(Long movieId) {
        MovieInfoResponse movieInfoResponse = movieQueryService.findMovieInfos(List.of(movieId)).get(movieId);
        if (movieInfoResponse == null || movieInfoResponse.deleted()) {
            throw new AppException("Không tìm thấy phim", HttpStatus.NOT_FOUND, "MOVIE_NOT_FOUND");
        }
    }

    private List<CinemaOptionResponse> resolveCinemas(Long cinemaId) {
        List<CinemaOptionResponse> activeCinemaOptionResponses = cinemaQueryService.findActiveCinemaOptions();
        if (cinemaId == null) {
            return activeCinemaOptionResponses;
        }
        List<CinemaOptionResponse> selectedCinemaOptionResponses = activeCinemaOptionResponses.stream()
                .filter(cinemaOptionResponse -> cinemaOptionResponse.id().equals(cinemaId))
                .toList();
        if (selectedCinemaOptionResponses.isEmpty()) {
            throw new AppException("Không tìm thấy cụm rạp", HttpStatus.NOT_FOUND, "CINEMA_NOT_FOUND");
        }
        return selectedCinemaOptionResponses;
    }

    private Map<Long, HallInfoResponse> findBookableHalls(List<CinemaOptionResponse> cinemaOptionResponses) {
        Set<Long> cinemaIds = cinemaOptionResponses.stream()
                .map(CinemaOptionResponse::id)
                .collect(Collectors.toSet());
        Set<Long> hallIds = cinemaQueryService.findHallIdsByCinemaIds(cinemaIds);
        return cinemaQueryService.findHallInfos(hallIds).values().stream()
                .filter(hallInfoResponse -> !hallInfoResponse.deleted() && hallInfoResponse.status() == HallStatus.ACTIVE)
                .collect(Collectors.toMap(HallInfoResponse::id, hallInfoResponse -> hallInfoResponse));
    }

    private List<Showtime> findShowtimes(Long movieId, Set<Long> hallIds, Instant windowFrom, Instant windowTo) {
        Specification<Showtime> specification = Specification.where(ShowtimeSpecifications.inHalls(hallIds))
                .and(ShowtimeSpecifications.startingBetween(windowFrom, windowTo))
                .and(ShowtimeSpecifications.withMovie(movieId))
                .and(ShowtimeSpecifications.withStatus(ShowtimeStatus.ACTIVE));
        return showtimeRepository.findAll(specification, SHOWTIME_SORT);
    }

    private BookableShowtimeResponse toBookableShowtime(
            Showtime showtime, HallInfoResponse hallInfoResponse, int totalSeats, int occupiedSeats) {
        int availableSeats = Math.max(0, totalSeats - occupiedSeats);
        return new BookableShowtimeResponse(
                showtime.getId(),
                hallInfoResponse.id(),
                hallInfoResponse.name(),
                hallInfoResponse.screenType(),
                showtime.getStartTime(),
                showtime.getEndTime(),
                showtime.getBasePrice(),
                totalSeats,
                availableSeats,
                availableSeats == 0);
    }
}
