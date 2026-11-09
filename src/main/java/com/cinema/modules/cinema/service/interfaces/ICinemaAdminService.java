package com.cinema.modules.cinema.service.interfaces;

import com.cinema.common.response.PageResponse;
import com.cinema.modules.cinema.dto.request.CinemaRequest;
import com.cinema.modules.cinema.dto.response.CinemaDetailResponse;
import com.cinema.modules.cinema.dto.response.CinemaResponse;
import com.cinema.modules.cinema.enums.CinemaStatus;

public interface ICinemaAdminService {

    PageResponse<CinemaResponse> searchCinemas(
            Long actorUserId, String keyword, String city, CinemaStatus status, int page, int size);

    CinemaDetailResponse getCinema(Long actorUserId, Long cinemaId);

    CinemaDetailResponse createCinema(CinemaRequest cinemaRequest);

    CinemaDetailResponse updateCinema(Long actorUserId, Long cinemaId, CinemaRequest cinemaRequest);

    void deleteCinema(Long cinemaId);
}
