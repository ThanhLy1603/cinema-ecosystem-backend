package com.cinema.modules.showtime.service;

import com.cinema.modules.showtime.dto.response.ShowtimeInfoResponse;
import com.cinema.modules.showtime.entity.Showtime;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import com.cinema.modules.showtime.repository.ShowtimeRepository;
import com.cinema.modules.showtime.service.interfaces.IShowtimeQueryService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ShowtimeQueryService implements IShowtimeQueryService {

    private final ShowtimeRepository showtimeRepository;

    public ShowtimeQueryService(ShowtimeRepository showtimeRepository) {
        this.showtimeRepository = showtimeRepository;
    }

    @Override
    public Optional<ShowtimeInfoResponse> findShowtimeInfo(Long showtimeId) {
        return showtimeRepository.findById(showtimeId).map(this::toShowtimeInfo);
    }

    @Override
    public List<ShowtimeInfoResponse> findActiveShowtimeInfosBetween(Instant endsAfter, Instant startsBefore) {
        return showtimeRepository.findByStatusAndTimeRange(ShowtimeStatus.ACTIVE, endsAfter, startsBefore).stream()
                .map(this::toShowtimeInfo)
                .toList();
    }

    private ShowtimeInfoResponse toShowtimeInfo(Showtime showtime) {
        return new ShowtimeInfoResponse(
                showtime.getId(),
                showtime.getMovieId(),
                showtime.getHallId(),
                showtime.getStartTime(),
                showtime.getEndTime(),
                showtime.getBasePrice(),
                showtime.getStatus());
    }
}
