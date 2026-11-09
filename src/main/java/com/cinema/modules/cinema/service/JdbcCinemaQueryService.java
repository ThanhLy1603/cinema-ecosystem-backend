package com.cinema.modules.cinema.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class JdbcCinemaQueryService implements CinemaQueryService {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public JdbcCinemaQueryService(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
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
                "select cinema.id from cinemas cinema where cinema.id in (:cinemaIds) and cinema.status = :status",
                mapSqlParameterSource,
                Long.class));
    }

    @Override
    public Set<Long> findAllCinemaIds() {
        return new HashSet<>(namedParameterJdbcTemplate.queryForList(
                "select cinema.id from cinemas cinema", new MapSqlParameterSource(), Long.class));
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
}
