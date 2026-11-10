package com.cinema.modules.movie.repository;

import com.cinema.modules.movie.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface GenreRepository extends JpaRepository<Genre, Long> {

    List<Genre> findAllByOrderByNameAsc();

    List<Genre> findAllByIdIn(Collection<Long> genreIds);

    boolean existsBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long genreId);

    @Query("select count(movie) from Movie movie join movie.genres genre where genre.id = :genreId")
    long countMoviesByGenreId(@Param("genreId") Long genreId);
}
