package com.cinema.modules.cinema.service;

import com.cinema.modules.cinema.dto.response.CinemaOptionResponse;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.dto.response.SeatPricingInfoResponse;
import com.cinema.modules.cinema.dto.response.SeatTypeSurchargeResponse;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CinemaQueryService implements ICinemaQueryService {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public CinemaQueryService(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Override
    public Set<Long> findActiveCinemaIds(Collection<Long> cinemaIds) {
        if (cinemaIds == null || cinemaIds.isEmpty()) {
            return Set.of();
        }
        MapSqlParameterSource mapSqlParameterSource =
                new MapSqlParameterSource("cinemaIds", cinemaIds).addValue("status", ACTIVE_STATUS);
        return new HashSet<>(namedParameterJdbcTemplate.queryForList(
                "select cinema.id from cinemas cinema where cinema.id in (:cinemaIds) and cinema.status = :status and cinema.is_deleted = false",
                mapSqlParameterSource,
                Long.class));
    }

    @Override
    public Set<Long> findAllCinemaIds() {
        return new HashSet<>(namedParameterJdbcTemplate.queryForList(
                "select cinema.id from cinemas cinema where cinema.is_deleted = false", new MapSqlParameterSource(), Long.class));
    }

    @Override
    public Map<Long, String> findCinemaNames(Collection<Long> cinemaIds) {
        Map<Long, String> cinemaNames = new HashMap<>();
        if (cinemaIds == null || cinemaIds.isEmpty()) {
            return cinemaNames;
        }
        namedParameterJdbcTemplate.query(
                "select cinema.id, cinema.name from cinemas cinema where cinema.id in (:cinemaIds)",
                new MapSqlParameterSource("cinemaIds", cinemaIds),
                resultSet -> {
                    cinemaNames.put(resultSet.getLong("id"), resultSet.getString("name"));
                });
        return cinemaNames;
    }

    @Override
    public List<CinemaOptionResponse> findActiveCinemaOptions() {
        return namedParameterJdbcTemplate.query(
                "select cinema.id, cinema.name, cinema.city from cinemas cinema "
                        + "where cinema.status = :status and cinema.is_deleted = false order by cinema.name, cinema.id",
                new MapSqlParameterSource("status", ACTIVE_STATUS),
                (resultSet, rowNumber) -> new CinemaOptionResponse(
                        resultSet.getLong("id"), resultSet.getString("name"), resultSet.getString("city")));
    }

    @Override
    public Map<Long, HallInfoResponse> findHallInfos(Collection<Long> hallIds) {
        Map<Long, HallInfoResponse> hallInfoResponses = new HashMap<>();
        if (hallIds == null || hallIds.isEmpty()) {
            return hallInfoResponses;
        }
        namedParameterJdbcTemplate.query(
                "select hall.id, hall.cinema_id, hall.name, hall.screen_type, hall.status, hall.is_deleted "
                        + "from halls hall where hall.id in (:hallIds)",
                new MapSqlParameterSource("hallIds", hallIds),
                resultSet -> {
                    HallInfoResponse hallInfoResponse = new HallInfoResponse(
                            resultSet.getLong("id"),
                            resultSet.getLong("cinema_id"),
                            resultSet.getString("name"),
                            ScreenType.fromCode(resultSet.getString("screen_type")),
                            HallStatus.valueOf(resultSet.getString("status")),
                            resultSet.getBoolean("is_deleted"));
                    hallInfoResponses.put(hallInfoResponse.id(), hallInfoResponse);
                });
        return hallInfoResponses;
    }

    @Override
    public Set<Long> findHallIdsByCinemaIds(Collection<Long> cinemaIds) {
        if (cinemaIds == null || cinemaIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(namedParameterJdbcTemplate.queryForList(
                "select hall.id from halls hall where hall.cinema_id in (:cinemaIds) and hall.is_deleted = false",
                new MapSqlParameterSource("cinemaIds", cinemaIds),
                Long.class));
    }

    @Override
    public Map<Long, SeatPricingInfoResponse> findSeatPricingInfos(Collection<Long> seatIds) {
        Map<Long, SeatPricingInfoResponse> seatPricingInfoResponses = new HashMap<>();
        if (seatIds == null || seatIds.isEmpty()) {
            return seatPricingInfoResponses;
        }
        namedParameterJdbcTemplate.query(
                "select seat.id, seat.hall_id, seat_type.code, seat_type.name, seat_type.default_surcharge, "
                        + "(seat.is_deleted = false and seat.status = :activeStatus) as bookable "
                        + "from seats seat join seat_types seat_type on seat_type.id = seat.seat_type_id "
                        + "where seat.id in (:seatIds)",
                new MapSqlParameterSource("seatIds", seatIds).addValue("activeStatus", ACTIVE_STATUS),
                resultSet -> {
                    SeatPricingInfoResponse seatPricingInfoResponse = new SeatPricingInfoResponse(
                            resultSet.getLong("id"),
                            resultSet.getLong("hall_id"),
                            resultSet.getString("code"),
                            resultSet.getString("name"),
                            resultSet.getBigDecimal("default_surcharge"),
                            resultSet.getBoolean("bookable"));
                    seatPricingInfoResponses.put(seatPricingInfoResponse.seatId(), seatPricingInfoResponse);
                });
        return seatPricingInfoResponses;
    }

    @Override
    public List<SeatTypeSurchargeResponse> findSeatTypeSurcharges() {
        return namedParameterJdbcTemplate.query(
                "select seat_type.code, seat_type.name, seat_type.default_surcharge "
                        + "from seat_types seat_type order by seat_type.default_surcharge, seat_type.id",
                new MapSqlParameterSource(),
                (resultSet, rowNumber) -> new SeatTypeSurchargeResponse(
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getBigDecimal("default_surcharge")));
    }
}
