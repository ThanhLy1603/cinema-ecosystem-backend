package com.cinema.modules.movie.specification;

import com.cinema.common.specification.SoftDeleteSpecifications;
import com.cinema.modules.movie.entity.Movie;
import com.cinema.modules.movie.entity.MovieCast;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class MovieSpecifications {

    private MovieSpecifications() {
    }

    public static Specification<Movie> search(
            String keyword, Long genreId, MovieStatus status, AgeRating rated, boolean deleted) {
        Specification<Movie> specification = Specification.where(SoftDeleteSpecifications.<Movie>deletedIs(deleted));
        specification = specification.and(statusFilter(status));
        specification = specification.and(ratedFilter(rated));
        specification = specification.and(genreFilter(genreId));
        specification = specification.and(keywordFilter(keyword));
        return specification;
    }

    static Specification<Movie> statusFilter(MovieStatus status) {
        if (status == null) {
            return null;
        }
        return (movie, criteriaQuery, criteriaBuilder) -> criteriaBuilder.equal(movie.get("status"), status);
    }

    static Specification<Movie> ratedFilter(AgeRating rated) {
        if (rated == null) {
            return null;
        }
        return (movie, criteriaQuery, criteriaBuilder) -> criteriaBuilder.equal(movie.get("rated"), rated);
    }

    static Specification<Movie> genreFilter(Long genreId) {
        if (genreId == null) {
            return null;
        }
        return (movie, criteriaQuery, criteriaBuilder) -> {
            Subquery<Long> genreSubquery = criteriaQuery.subquery(Long.class);
            Root<Movie> subqueryMovie = genreSubquery.from(Movie.class);
            Join<Movie, ?> genre = subqueryMovie.join("genres");
            genreSubquery.select(subqueryMovie.<Long>get("id")).where(
                    criteriaBuilder.equal(subqueryMovie.get("id"), movie.get("id")),
                    criteriaBuilder.equal(genre.get("id"), genreId));
            return criteriaBuilder.exists(genreSubquery);
        };
    }

    static Specification<Movie> keywordFilter(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        return (movie, criteriaQuery, criteriaBuilder) -> {
            Subquery<Long> genreSubquery = criteriaQuery.subquery(Long.class);
            Root<Movie> subqueryMovie = genreSubquery.from(Movie.class);
            Join<Movie, ?> genre = subqueryMovie.join("genres");
            genreSubquery.select(subqueryMovie.<Long>get("id")).where(
                    criteriaBuilder.equal(subqueryMovie.get("id"), movie.get("id")),
                    criteriaBuilder.like(criteriaBuilder.lower(genre.get("name")), pattern, '\\'));

            Subquery<Long> movieCastSubquery = criteriaQuery.subquery(Long.class);
            Root<MovieCast> movieCast = movieCastSubquery.from(MovieCast.class);
            Join<MovieCast, ?> person = movieCast.join("person");
            movieCastSubquery.select(movieCast.<Long>get("id")).where(
                    criteriaBuilder.equal(movieCast.get("movieId"), movie.get("id")),
                    criteriaBuilder.like(criteriaBuilder.lower(person.get("name")), pattern, '\\'));

            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(movie.get("title")), pattern, '\\'),
                    criteriaBuilder.like(criteriaBuilder.lower(movie.get("originalTitle")), pattern, '\\'),
                    criteriaBuilder.exists(genreSubquery),
                    criteriaBuilder.exists(movieCastSubquery));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
