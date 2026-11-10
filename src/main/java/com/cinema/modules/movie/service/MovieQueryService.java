package com.cinema.modules.movie.service;

import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MovieQueryService implements IMovieQueryService {

    private static final String SELECT_MOVIE_INFO =
            "select movie.id, movie.title, movie.slug, movie.duration_minutes, movie.rated, movie.poster_url, movie.status, "
                    + "movie.is_deleted from movies movie ";

    private static final RowMapper<MovieInfoResponse> MOVIE_INFO_ROW_MAPPER = (resultSet, rowNumber) ->
            new MovieInfoResponse(
                    resultSet.getLong("id"),
                    resultSet.getString("title"),
                    resultSet.getString("slug"),
                    resultSet.getInt("duration_minutes"),
                    AgeRating.valueOf(resultSet.getString("rated")),
                    resultSet.getString("poster_url"),
                    MovieStatus.valueOf(resultSet.getString("status")),
                    resultSet.getBoolean("is_deleted"));

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public MovieQueryService(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Override
    public Map<Long, MovieInfoResponse> findMovieInfos(Collection<Long> movieIds) {
        Map<Long, MovieInfoResponse> movieInfoResponses = new HashMap<>();
        if (movieIds == null || movieIds.isEmpty()) {
            return movieInfoResponses;
        }
        namedParameterJdbcTemplate
                .query(SELECT_MOVIE_INFO + "where movie.id in (:movieIds)",
                        new MapSqlParameterSource("movieIds", movieIds),
                        MOVIE_INFO_ROW_MAPPER)
                .forEach(movieInfoResponse -> movieInfoResponses.put(movieInfoResponse.id(), movieInfoResponse));
        return movieInfoResponses;
    }

    @Override
    public List<MovieInfoResponse> findSchedulableMovieInfos() {
        return namedParameterJdbcTemplate.query(
                SELECT_MOVIE_INFO + "where movie.is_deleted = false and movie.status in ('NOW_SHOWING', 'COMING_SOON') "
                        + "order by movie.status desc, movie.title, movie.id",
                new MapSqlParameterSource(),
                MOVIE_INFO_ROW_MAPPER);
    }
}
