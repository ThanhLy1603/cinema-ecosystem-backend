package com.cinema.modules.cinema.service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface CinemaQueryService {

    Set<Long> findActiveCinemaIds(Collection<Long> cinemaIds);

    Set<Long> findAllCinemaIds();

    Map<Long, String> findCinemaNames(Collection<Long> cinemaIds);
}
