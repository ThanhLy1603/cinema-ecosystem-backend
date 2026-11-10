package com.cinema.modules.showtime.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.util.BusinessZone;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import com.cinema.modules.showtime.dto.response.PublicMovieShowtimesResponse;
import com.cinema.modules.showtime.dto.response.PublicShowtimeResponse;
import com.cinema.modules.showtime.entity.Showtime;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import com.cinema.modules.showtime.repository.ShowtimeRepository;
import com.cinema.modules.showtime.service.interfaces.IShowtimePublicService;
import com.cinema.modules.showtime.specification.ShowtimeSpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ShowtimePublicService implements IShowtimePublicService {

    private static final Sort SHOWTIME_SORT = Sort.by("startTime", "id");

    private final ShowtimeRepository showtimeRepository;
    private final ICinemaQueryService cinemaQueryService;
    private final IMovieQueryService movieQueryService;
    private final Clock clock;

    public ShowtimePublicService(
            ShowtimeRepository showtimeRepository,
            ICinemaQueryService cinemaQueryService,
            IMovieQueryService movieQueryService,
            Clock clock) {
        this.showtimeRepository = showtimeRepository;
        this.cinemaQueryService = cinemaQueryService;
        this.movieQueryService = movieQueryService;
        this.clock = clock;
    }

    @Override
    public List<PublicMovieShowtimesResponse> findShowtimes(Long cinemaId, LocalDate date, Long movieId) {
        if (cinemaQueryService.findActiveCinemaIds(List.of(cinemaId)).isEmpty()) {
            throw new AppException("Không tìm thấy cụm rạp", HttpStatus.NOT_FOUND, "CINEMA_NOT_FOUND");
        }
        Instant now = Instant.now(clock);
        LocalDate showtimeDate = date == null ? BusinessZone.toLocalDate(now) : date;
        Map<Long, HallInfoResponse> activeHallInfoResponses = findActiveHallInfos(cinemaId);
        if (activeHallInfoResponses.isEmpty()) {
            return List.of();
        }
        Instant windowStart = BusinessZone.startOfDay(showtimeDate);
        Instant windowFrom = windowStart.isBefore(now) ? now : windowStart;
        Specification<Showtime> specification =
                Specification.where(ShowtimeSpecifications.inHalls(activeHallInfoResponses.keySet()))
                        .and(ShowtimeSpecifications.startingBetween(windowFrom, BusinessZone.startOfNextDay(showtimeDate)))
                        .and(ShowtimeSpecifications.withMovie(movieId))
                        .and(ShowtimeSpecifications.withStatus(ShowtimeStatus.ACTIVE));
        List<Showtime> showtimes = showtimeRepository.findAll(specification, SHOWTIME_SORT);
        return groupByMovie(showtimes, activeHallInfoResponses);
    }

    private Map<Long, HallInfoResponse> findActiveHallInfos(Long cinemaId) {
        Set<Long> hallIds = cinemaQueryService.findHallIdsByCinemaIds(List.of(cinemaId));
        return cinemaQueryService.findHallInfos(hallIds).values().stream()
                .filter(hallInfoResponse -> !hallInfoResponse.deleted() && hallInfoResponse.status() == HallStatus.ACTIVE)
                .collect(Collectors.toMap(HallInfoResponse::id, hallInfoResponse -> hallInfoResponse));
    }

    private List<PublicMovieShowtimesResponse> groupByMovie(
            List<Showtime> showtimes, Map<Long, HallInfoResponse> hallInfoResponses) {
        if (showtimes.isEmpty()) {
            return List.of();
        }
        Map<Long, MovieInfoResponse> movieInfoResponses = movieQueryService.findMovieInfos(
                showtimes.stream().map(Showtime::getMovieId).collect(Collectors.toSet()));
        Map<Long, List<PublicShowtimeResponse>> showtimesByMovieId = new LinkedHashMap<>();
        for (Showtime showtime : showtimes) {
            MovieInfoResponse movieInfoResponse = movieInfoResponses.get(showtime.getMovieId());
            if (movieInfoResponse == null || movieInfoResponse.deleted()) {
                continue;
            }
            HallInfoResponse hallInfoResponse = hallInfoResponses.get(showtime.getHallId());
            showtimesByMovieId
                    .computeIfAbsent(showtime.getMovieId(), movieId -> new ArrayList<>())
                    .add(new PublicShowtimeResponse(
                            showtime.getId(),
                            hallInfoResponse.id(),
                            hallInfoResponse.name(),
                            hallInfoResponse.screenType(),
                            showtime.getStartTime(),
                            showtime.getEndTime(),
                            showtime.getBasePrice()));
        }
        List<PublicMovieShowtimesResponse> publicMovieShowtimesResponses = new ArrayList<>();
        showtimesByMovieId.forEach((movieId, publicShowtimeResponses) -> publicMovieShowtimesResponses.add(
                toMovieShowtimes(movieInfoResponses.get(movieId), publicShowtimeResponses)));
        return publicMovieShowtimesResponses;
    }

    private PublicMovieShowtimesResponse toMovieShowtimes(
            MovieInfoResponse movieInfoResponse, List<PublicShowtimeResponse> publicShowtimeResponses) {
        return new PublicMovieShowtimesResponse(
                movieInfoResponse.id(),
                movieInfoResponse.slug(),
                movieInfoResponse.title(),
                movieInfoResponse.posterUrl(),
                movieInfoResponse.rated(),
                movieInfoResponse.durationMinutes(),
                publicShowtimeResponses);
    }
}
