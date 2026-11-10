package com.cinema.modules.movie.repository;

import com.cinema.modules.movie.entity.Movie;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long>, JpaSpecificationExecutor<Movie> {

    @EntityGraph(attributePaths = "genres")
    List<Movie> findWithGenresByIdIn(Collection<Long> movieIds);

    @EntityGraph(attributePaths = "genres")
    Optional<Movie> findWithGenresByIdAndDeleted(Long movieId, boolean deleted);

    @EntityGraph(attributePaths = "genres")
    Optional<Movie> findWithGenresBySlugAndDeleted(String slug, boolean deleted);

    boolean existsBySlugAndDeletedFalse(String slug);

    boolean existsBySlugAndDeletedFalseAndIdNot(String slug, Long movieId);
}
