package com.cinema.modules.community.service.interfaces;

import com.cinema.modules.community.dto.response.MovieRatingSummaryResponse;

import java.util.Collection;
import java.util.List;

public interface IMovieRatingService {

    List<MovieRatingSummaryResponse> findSummaries(Collection<Long> movieIds);
}
