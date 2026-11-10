package com.cinema.modules.showtime.service.interfaces;

import com.cinema.modules.showtime.dto.request.ShowtimeDayCopyRequest;
import com.cinema.modules.showtime.dto.request.ShowtimeDuplicateRequest;
import com.cinema.modules.showtime.dto.response.BulkShowtimeResponse;

public interface IShowtimeBulkService {

    BulkShowtimeResponse duplicateShowtime(Long actorUserId, ShowtimeDuplicateRequest showtimeDuplicateRequest);

    BulkShowtimeResponse copyDay(Long actorUserId, ShowtimeDayCopyRequest showtimeDayCopyRequest);
}
