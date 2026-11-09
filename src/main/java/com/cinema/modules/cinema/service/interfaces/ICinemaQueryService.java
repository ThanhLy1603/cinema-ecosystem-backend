package com.cinema.modules.cinema.service.interfaces;

import com.cinema.modules.cinema.dto.response.CinemaOptionResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ICinemaQueryService {

    Set<Long> findActiveCinemaIds(Collection<Long> cinemaIds);

    Set<Long> findAllCinemaIds();

    Map<Long, String> findCinemaNames(Collection<Long> cinemaIds);

    List<CinemaOptionResponse> findActiveCinemaOptions();
}
