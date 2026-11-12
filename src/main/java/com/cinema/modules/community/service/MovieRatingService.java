package com.cinema.modules.community.service;

import com.cinema.modules.community.dto.response.MovieRatingSummaryResponse;
import com.cinema.modules.community.repository.MovieReviewRepository;
import com.cinema.modules.community.service.interfaces.IMovieRatingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
public class MovieRatingService implements IMovieRatingService {

    private static final int MAX_MOVIE_IDS = 100;

    private final MovieReviewRepository movieReviewRepository;

    public MovieRatingService(MovieReviewRepository movieReviewRepository) {
        this.movieReviewRepository = movieReviewRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public List<MovieRatingSummaryResponse> findSummaries(Collection<Long> movieIds) {
        if (movieIds.isEmpty()) {
            return List.of();
        }
        List<Long> limitedMovieIds = movieIds.stream().distinct().limit(MAX_MOVIE_IDS).toList();
        return movieReviewRepository.summarizeByMovieIds(limitedMovieIds);
    }
}
