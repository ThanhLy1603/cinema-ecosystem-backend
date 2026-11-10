package com.cinema.modules.movie.service.interfaces;

import com.cinema.common.response.PageResponse;
import com.cinema.modules.movie.dto.request.MovieRequest;
import com.cinema.modules.movie.dto.response.MovieDetailResponse;
import com.cinema.modules.movie.dto.response.MovieResponse;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;

public interface IMovieAdminService {

    PageResponse<MovieResponse> searchMovies(
            String keyword, Long genreId, MovieStatus status, AgeRating rated, boolean deleted, int page, int size);

    MovieDetailResponse getMovie(Long movieId);

    MovieDetailResponse createMovie(MovieRequest movieRequest);

    MovieDetailResponse updateMovie(Long movieId, MovieRequest movieRequest);

    void deleteMovie(Long movieId);

    MovieDetailResponse restoreMovie(Long movieId);
}
