package com.cinema.modules.booking.service.interfaces;

import com.cinema.modules.booking.dto.response.ShowtimeTicketStatsResponse;

import java.util.Collection;
import java.util.Map;

public interface IShowtimeTicketStatsService {

    Map<Long, ShowtimeTicketStatsResponse> countByShowtimes(Collection<Long> showtimeIds);
}
