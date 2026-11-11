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
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MoviePublicService implements IMoviePublicService {

    private final MovieViewAssembler movieViewAssembler;
    private final GenreRepository genreRepository;
    private final PublicMovieCache publicMovieCache;

    public MoviePublicService(
            MovieViewAssembler movieViewAssembler,
            GenreRepository genreRepository,
            PublicMovieCache publicMovieCache) {
        this.movieViewAssembler = movieViewAssembler;
        this.genreRepository = genreRepository;
        this.publicMovieCache = publicMovieCache;
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<MovieResponse> searchMovies(
            String keyword, Long genreId, MovieStatus status, AgeRating rated, int page, int size) {
        if (keyword != null && !keyword.isBlank()) {
            return loadMovies(keyword, genreId, status, rated, page, size);
        }
        String cacheName = "list:" + status + ":" + genreId + ":" + rated + ":" + page + ":" + size;
        return publicMovieCache.getOrLoad(
                cacheName,
                new TypeReference<PageResponse<MovieResponse>>() { },
                () -> loadMovies(null, genreId, status, rated, page, size));
    }

    @Transactional(readOnly = true)
    @Override
    public MovieDetailResponse getMovieBySlug(String movieSlug) {
        return publicMovieCache.getOrLoad(
                "detail:" + movieSlug,
                new TypeReference<MovieDetailResponse>() { },
                () -> movieViewAssembler.getMovieDetailBySlug(movieSlug));
    }

    @Transactional(readOnly = true)
    @Override
    public List<GenreResponse> findGenres() {
        return publicMovieCache.getOrLoad(
                "genres",
                new TypeReference<List<GenreResponse>>() { },
                () -> genreRepository.findAllByOrderByNameAsc().stream().map(MovieMapper::toGenreResponse).toList());
    }

    private PageResponse<MovieResponse> loadMovies(
            String keyword, Long genreId, MovieStatus status, AgeRating rated, int page, int size) {
        return movieViewAssembler.searchMovies(
                MovieSpecifications.search(keyword, genreId, status, rated, false), page, size);
    }
}
