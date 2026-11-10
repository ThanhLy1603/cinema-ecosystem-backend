package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.movie.dto.response.MovieDetailResponse;
import com.cinema.modules.movie.dto.response.MovieResponse;
import com.cinema.modules.movie.entity.Movie;
import com.cinema.modules.movie.entity.MovieCast;
import com.cinema.modules.movie.repository.MovieCastRepository;
import com.cinema.modules.movie.repository.MovieRepository;
import com.cinema.modules.movie.util.MovieMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MovieViewAssembler {

    static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final MovieRepository movieRepository;
    private final MovieCastRepository movieCastRepository;

    public MovieViewAssembler(MovieRepository movieRepository, MovieCastRepository movieCastRepository) {
        this.movieRepository = movieRepository;
        this.movieCastRepository = movieCastRepository;
    }

    public PageResponse<MovieResponse> searchMovies(Specification<Movie> specification, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("releaseDate"), Sort.Order.desc("id")));
        Page<Movie> moviePage = movieRepository.findAll(specification, pageable);
        List<Long> movieIds = moviePage.getContent().stream().map(Movie::getId).toList();
        Map<Long, Movie> moviesWithGenres = movieIds.isEmpty()
                ? Map.of()
                : movieRepository.findWithGenresByIdIn(movieIds).stream()
                        .collect(Collectors.toMap(Movie::getId, Function.identity()));
        List<MovieResponse> movieResponses = movieIds.stream()
                .map(movieId -> MovieMapper.toMovieResponse(moviesWithGenres.get(movieId)))
                .toList();
        return PageResponse.of(moviePage, movieResponses);
    }

    public MovieDetailResponse getMovieDetail(Long movieId) {
        Movie movie = loadMovieWithGenres(movieId, false);
        List<MovieCast> movieCasts = movieCastRepository.findByMovieIdInOrderByIdAsc(List.of(movieId));
        return MovieMapper.toMovieDetailResponse(movie, movieCasts);
    }

    public Movie loadMovieWithGenres(Long movieId, boolean deleted) {
        return movieRepository.findWithGenresByIdAndDeleted(movieId, deleted)
                .orElseThrow(MovieViewAssembler::movieNotFound);
    }

    public static AppException movieNotFound() {
        return new AppException("Không tìm thấy phim", HttpStatus.NOT_FOUND, "MOVIE_NOT_FOUND");
    }
}
