package com.cinema.modules.showtime.service;

import com.cinema.modules.showtime.dto.response.ShowtimeInfoResponse;
import com.cinema.modules.showtime.repository.ShowtimeRepository;
import com.cinema.modules.showtime.service.interfaces.IShowtimeQueryService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ShowtimeQueryService implements IShowtimeQueryService {

    private final ShowtimeRepository showtimeRepository;

    public ShowtimeQueryService(ShowtimeRepository showtimeRepository) {
        this.showtimeRepository = showtimeRepository;
    }

    @Override
    public Optional<ShowtimeInfoResponse> findShowtimeInfo(Long showtimeId) {
        return showtimeRepository.findById(showtimeId).map(showtime -> new ShowtimeInfoResponse(
                showtime.getId(),
                showtime.getMovieId(),
                showtime.getHallId(),
                showtime.getStartTime(),
                showtime.getEndTime(),
                showtime.getBasePrice(),
                showtime.getStatus()));
    }
}
