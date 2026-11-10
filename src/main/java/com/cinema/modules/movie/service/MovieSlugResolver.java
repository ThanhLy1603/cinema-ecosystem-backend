package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.util.SlugGenerator;
import com.cinema.modules.movie.entity.Movie;
import com.cinema.modules.movie.repository.MovieRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MovieSlugResolver {

    private static final int MAX_BASE_SLUG_LENGTH = 200;
    private static final int MAX_SLUG_ATTEMPTS = 50;
    private static final String FALLBACK_SLUG = "phim";

    private final MovieRepository movieRepository;

    public MovieSlugResolver(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public String resolveForCreate(String requestedSlug, String title) {
        if (requestedSlug == null || requestedSlug.isBlank()) {
            return generateUniqueSlug(title);
        }
        String slug = requestedSlug.trim();
        if (movieRepository.existsBySlugAndDeletedFalse(slug)) {
            throw slugAlreadyUsed();
        }
        return slug;
    }

    public String resolveForUpdate(Movie movie, String requestedSlug) {
        if (requestedSlug == null || requestedSlug.isBlank()) {
            return movie.getSlug();
        }
        String slug = requestedSlug.trim();
        if (slug.equals(movie.getSlug())) {
            return slug;
        }
        if (movieRepository.existsBySlugAndDeletedFalseAndIdNot(slug, movie.getId())) {
            throw slugAlreadyUsed();
        }
        return slug;
    }

    public String resolveForRestore(Movie movie) {
        if (!movieRepository.existsBySlugAndDeletedFalseAndIdNot(movie.getSlug(), movie.getId())) {
            return movie.getSlug();
        }
        return generateUniqueSlug(movie.getTitle());
    }

    private String generateUniqueSlug(String title) {
        String baseSlug = truncate(SlugGenerator.toSlug(title, FALLBACK_SLUG));
        String slug = baseSlug;
        for (int attempt = 2; movieRepository.existsBySlugAndDeletedFalse(slug); attempt++) {
            if (attempt > MAX_SLUG_ATTEMPTS) {
                throw new AppException("Không tạo được đường dẫn cho phim", HttpStatus.CONFLICT, "SLUG_EXHAUSTED");
            }
            slug = baseSlug + "-" + attempt;
        }
        return slug;
    }

    private static String truncate(String slug) {
        if (slug.length() <= MAX_BASE_SLUG_LENGTH) {
            return slug;
        }
        String truncatedSlug = slug.substring(0, MAX_BASE_SLUG_LENGTH);
        int lastHyphenIndex = truncatedSlug.lastIndexOf('-');
        return lastHyphenIndex > 0 ? truncatedSlug.substring(0, lastHyphenIndex) : truncatedSlug;
    }

    private static AppException slugAlreadyUsed() {
        return new AppException("Đường dẫn phim đã được dùng cho phim khác", HttpStatus.CONFLICT, "MOVIE_SLUG_EXISTS");
    }
}
