package com.cinema.modules.movie.service.interfaces;

import com.cinema.modules.movie.dto.response.MovieInfoResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface IMovieQueryService {

    Map<Long, MovieInfoResponse> findMovieInfos(Collection<Long> movieIds);

    List<MovieInfoResponse> findSchedulableMovieInfos();
}
