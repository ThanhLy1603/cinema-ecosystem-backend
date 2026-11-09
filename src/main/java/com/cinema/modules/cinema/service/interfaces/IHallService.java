package com.cinema.modules.cinema.service.interfaces;

import com.cinema.modules.cinema.dto.request.HallRequest;
import com.cinema.modules.cinema.dto.response.HallResponse;

import java.util.List;

public interface IHallService {

    List<HallResponse> listHalls(Long actorUserId, Long cinemaId, boolean deleted);

    HallResponse createHall(Long actorUserId, Long cinemaId, HallRequest hallRequest);

    HallResponse updateHall(Long actorUserId, Long hallId, HallRequest hallRequest);

    void deleteHall(Long actorUserId, Long hallId);

    HallResponse restoreHall(Long actorUserId, Long hallId);
}
