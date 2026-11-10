package com.cinema.modules.movie.service.interfaces;

import com.cinema.common.response.PageResponse;
import com.cinema.modules.movie.dto.response.GenreResponse;
import com.cinema.modules.movie.dto.response.MovieDetailResponse;
import com.cinema.modules.movie.dto.response.MovieResponse;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;

import java.util.List;

public interface IMoviePublicService {

    PageResponse<MovieResponse> searchMovies(
            String keyword, Long genreId, MovieStatus status, AgeRating rated, int page, int size);

    MovieDetailResponse getMovieBySlug(String movieSlug);

    List<GenreResponse> findGenres();
}
