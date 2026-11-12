package com.cinema.modules.community.repository;

import com.cinema.modules.community.dto.response.MovieRatingSummaryResponse;
import com.cinema.modules.community.entity.MovieReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MovieReviewRepository extends JpaRepository<MovieReview, Long> {

    @Query("select new com.cinema.modules.community.dto.response.MovieRatingSummaryResponse("
            + "movieReview.movieId, avg(movieReview.rating), count(movieReview)) "
            + "from MovieReview movieReview where movieReview.movieId in :movieIds "
            + "group by movieReview.movieId")
    List<MovieRatingSummaryResponse> summarizeByMovieIds(@Param("movieIds") Collection<Long> movieIds);
}
