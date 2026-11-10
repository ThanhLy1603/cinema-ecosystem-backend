package com.cinema.modules.movie.util;

import com.cinema.modules.movie.dto.response.GenreResponse;
import com.cinema.modules.movie.dto.response.MovieCastResponse;
import com.cinema.modules.movie.dto.response.MovieDetailResponse;
import com.cinema.modules.movie.dto.response.MovieResponse;
import com.cinema.modules.movie.dto.response.PersonResponse;
import com.cinema.modules.movie.entity.Genre;
import com.cinema.modules.movie.entity.Movie;
import com.cinema.modules.movie.entity.MovieCast;
import com.cinema.modules.movie.entity.Person;

import java.util.Comparator;
import java.util.List;

public final class MovieMapper {

    private MovieMapper() {
    }

    public static GenreResponse toGenreResponse(Genre genre) {
        return new GenreResponse(genre.getId(), genre.getName(), genre.getSlug());
    }

    public static PersonResponse toPersonResponse(Person person) {
        return new PersonResponse(person.getId(), person.getName(), person.getAvatarUrl(), person.getBio());
    }

    public static MovieCastResponse toMovieCastResponse(MovieCast movieCast) {
        Person person = movieCast.getPerson();
        return new MovieCastResponse(
                person.getId(), person.getName(), person.getAvatarUrl(), movieCast.getRoleType(), movieCast.getCharacterName());
    }

    public static MovieResponse toMovieResponse(Movie movie) {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getSlug(),
                movie.getOriginalTitle(),
                movie.getDurationMinutes(),
                movie.getReleaseDate(),
                movie.getEndDate(),
                movie.getRated(),
                movie.getPosterUrl(),
                movie.getBannerUrl(),
                movie.getTrailerUrl(),
                movie.getStatus(),
                toGenreResponses(movie),
                movie.isDeleted());
    }

    public static MovieDetailResponse toMovieDetailResponse(Movie movie, List<MovieCast> movieCasts) {
        return new MovieDetailResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getSlug(),
                movie.getOriginalTitle(),
                movie.getDurationMinutes(),
                movie.getReleaseDate(),
                movie.getEndDate(),
                movie.getRated(),
                movie.getPosterUrl(),
                movie.getBannerUrl(),
                movie.getTrailerUrl(),
                movie.getDescription(),
                movie.getStatus(),
                toGenreResponses(movie),
                movieCasts.stream().map(MovieMapper::toMovieCastResponse).toList(),
                movie.isDeleted());
    }

    private static List<GenreResponse> toGenreResponses(Movie movie) {
        return movie.getGenres().stream()
                .sorted(Comparator.comparing(Genre::getName).thenComparing(Genre::getId))
                .map(MovieMapper::toGenreResponse)
                .toList();
    }
}
