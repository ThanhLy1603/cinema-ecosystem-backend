package com.cinema.modules.cinema.service.interfaces;

import com.cinema.modules.cinema.dto.response.CinemaDetailResponse;
import com.cinema.modules.cinema.dto.response.CinemaResponse;
import com.cinema.modules.cinema.dto.response.SeatLayoutResponse;
import com.cinema.modules.cinema.dto.response.SeatTypeResponse;

import java.util.List;

public interface ICinemaPublicService {

    List<CinemaResponse> findPublicCinemas(String city);

    CinemaDetailResponse findPublicCinemaBySlug(String slug);

    List<String> findPublicCities();

    List<SeatTypeResponse> findSeatTypes();

    SeatLayoutResponse findPublicSeatLayout(Long hallId);
}
