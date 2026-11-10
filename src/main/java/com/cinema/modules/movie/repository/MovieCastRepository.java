package com.cinema.modules.movie.repository;

import com.cinema.modules.movie.entity.MovieCast;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MovieCastRepository extends JpaRepository<MovieCast, Long> {

    @EntityGraph(attributePaths = "person")
    List<MovieCast> findByMovieIdInOrderByIdAsc(Collection<Long> movieIds);

    boolean existsByPersonId(Long personId);

    @Modifying(flushAutomatically = true)
    @Query("delete from MovieCast movieCast where movieCast.movieId = :movieId")
    void deleteAllByMovieId(@Param("movieId") Long movieId);
}
