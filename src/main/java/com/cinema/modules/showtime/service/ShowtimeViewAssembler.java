package com.cinema.modules.showtime.service;

import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import com.cinema.modules.showtime.dto.response.ShowtimeResponse;
import com.cinema.modules.showtime.entity.Showtime;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import com.cinema.modules.showtime.util.ShowtimeRules;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ShowtimeViewAssembler {

    private final IMovieQueryService movieQueryService;
    private final ICinemaQueryService cinemaQueryService;
    private final ShowtimeBookingChecker showtimeBookingChecker;

    public ShowtimeViewAssembler(
            IMovieQueryService movieQueryService,
            ICinemaQueryService cinemaQueryService,
            ShowtimeBookingChecker showtimeBookingChecker) {
        this.movieQueryService = movieQueryService;
        this.cinemaQueryService = cinemaQueryService;
        this.showtimeBookingChecker = showtimeBookingChecker;
    }

    public List<ShowtimeResponse> toShowtimeResponses(List<Showtime> showtimes, Instant now) {
        if (showtimes.isEmpty()) {
            return List.of();
        }
        Map<Long, MovieInfoResponse> movieInfoResponses = movieQueryService.findMovieInfos(
                showtimes.stream().map(Showtime::getMovieId).collect(Collectors.toSet()));
        Map<Long, HallInfoResponse> hallInfoResponses = cinemaQueryService.findHallInfos(
                showtimes.stream().map(Showtime::getHallId).collect(Collectors.toSet()));
        Set<Long> showtimeIdsWithBookings = showtimeBookingChecker.findShowtimeIdsWithBookings(
                showtimes.stream().map(Showtime::getId).toList(), now);
        return showtimes.stream()
                .map(showtime -> toShowtimeResponse(
                        showtime,
                        movieInfoResponses.get(showtime.getMovieId()),
                        hallInfoResponses.get(showtime.getHallId()),
                        showtimeIdsWithBookings.contains(showtime.getId()),
                        now))
                .toList();
    }

    private ShowtimeResponse toShowtimeResponse(
            Showtime showtime,
            MovieInfoResponse movieInfoResponse,
            HallInfoResponse hallInfoResponse,
            boolean hasBookings,
            Instant now) {
        boolean active = showtime.getStatus() == ShowtimeStatus.ACTIVE;
        return new ShowtimeResponse(
                showtime.getId(),
                showtime.getMovieId(),
                movieInfoResponse.title(),
                movieInfoResponse.rated(),
                movieInfoResponse.durationMinutes(),
                hallInfoResponse.cinemaId(),
                showtime.getHallId(),
                hallInfoResponse.name(),
                hallInfoResponse.screenType(),
                showtime.getStartTime(),
                showtime.getEndTime(),
                ShowtimeRules.calculateOccupiedUntil(showtime.getEndTime()),
                showtime.getBasePrice(),
                showtime.getStatus(),
                hasBookings,
                active && showtime.getStartTime().isAfter(now) && !hasBookings,
                active && showtime.getEndTime().isAfter(now) && !hasBookings);
    }
}
