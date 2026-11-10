package com.cinema.modules.movie.service;

import com.cinema.common.response.PageResponse;
import com.cinema.modules.movie.dto.response.GenreResponse;
import com.cinema.modules.movie.dto.response.MovieDetailResponse;
import com.cinema.modules.movie.dto.response.MovieResponse;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.repository.GenreRepository;
import com.cinema.modules.movie.service.interfaces.IMoviePublicService;
import com.cinema.modules.movie.specification.MovieSpecifications;
import com.cinema.modules.movie.util.MovieMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MoviePublicService implements IMoviePublicService {

    private final MovieViewAssembler movieViewAssembler;
    private final GenreRepository genreRepository;

    public MoviePublicService(MovieViewAssembler movieViewAssembler, GenreRepository genreRepository) {
        this.movieViewAssembler = movieViewAssembler;
        this.genreRepository = genreRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<MovieResponse> searchMovies(
            String keyword, Long genreId, MovieStatus status, AgeRating rated, int page, int size) {
        return movieViewAssembler.searchMovies(
                MovieSpecifications.search(keyword, genreId, status, rated, false), page, size);
    }

    @Transactional(readOnly = true)
    @Override
    public MovieDetailResponse getMovie(Long movieId) {
        return movieViewAssembler.getMovieDetail(movieId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<GenreResponse> findGenres() {
        return genreRepository.findAllByOrderByNameAsc().stream().map(MovieMapper::toGenreResponse).toList();
    }
}
