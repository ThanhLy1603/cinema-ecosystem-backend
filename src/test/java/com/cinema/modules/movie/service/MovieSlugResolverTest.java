package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.movie.entity.Movie;
import com.cinema.modules.movie.repository.MovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieSlugResolverTest {

    private static final Long MOVIE_ID = 7L;

    @Mock
    private MovieRepository movieRepository;

    private MovieSlugResolver movieSlugResolver;

    @BeforeEach
    void setUp() {
        movieSlugResolver = new MovieSlugResolver(movieRepository);
    }

    @Test
    void createGeneratesAccentFreeSlugFromTitle() {
        assertEquals("dao-pho-va-piano", movieSlugResolver.resolveForCreate(null, "Đào, Phở và Piano"));
    }

    @Test
    void createTreatsBlankSlugAsMissing() {
        assertEquals("mai", movieSlugResolver.resolveForCreate("  ", "Mai"));
    }

    @Test
    void createAddsNumericSuffixWhenSlugIsTaken() {
        when(movieRepository.existsBySlug("mai")).thenReturn(true);
        when(movieRepository.existsBySlug("mai-2")).thenReturn(true);

        assertEquals("mai-3", movieSlugResolver.resolveForCreate(null, "Mai"));
    }

    @Test
    void createUsesFallbackWhenTitleHasNoLettersOrDigits() {
        assertEquals("phim", movieSlugResolver.resolveForCreate(null, "!!!"));
    }

    @Test
    void createCutsVeryLongTitleAtWordBoundary() {
        String longTitle = "phim ".repeat(80);

        String slug = movieSlugResolver.resolveForCreate(null, longTitle);

        assertTrue(slug.length() <= 200);
        assertTrue(slug.matches("^[a-z0-9]+(-[a-z0-9]+)*$"));
    }

    @Test
    void createGivesUpAfterTooManyCollisions() {
        when(movieRepository.existsBySlug(anyString())).thenReturn(true);

        AppException appException =
                assertThrows(AppException.class, () -> movieSlugResolver.resolveForCreate(null, "Mai"));

        assertEquals("SLUG_EXHAUSTED", appException.getErrorCode());
    }

    @Test
    void createUsesRequestedSlugWhenFree() {
        assertEquals("mai-2026", movieSlugResolver.resolveForCreate(" mai-2026 ", "Mai"));
    }

    @Test
    void createRejectsRequestedSlugOfAnotherMovie() {
        when(movieRepository.existsBySlug("mai")).thenReturn(true);

        AppException appException =
                assertThrows(AppException.class, () -> movieSlugResolver.resolveForCreate("mai", "Khác"));

        assertEquals(HttpStatus.CONFLICT, appException.getStatus());
        assertEquals("MOVIE_SLUG_EXISTS", appException.getErrorCode());
    }

    @Test
    void updateKeepsCurrentSlugWhenRequestIsBlank() {
        assertEquals("mai", movieSlugResolver.resolveForUpdate(movie("mai"), null));
        assertEquals("mai", movieSlugResolver.resolveForUpdate(movie("mai"), ""));
        verify(movieRepository, never()).existsBySlugAndIdNot(anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void updateDoesNotRecheckUnchangedSlug() {
        assertEquals("mai", movieSlugResolver.resolveForUpdate(movie("mai"), "mai"));
        verify(movieRepository, never()).existsBySlugAndIdNot(anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void updateChangesSlugWhenFree() {
        assertEquals("mai-2026", movieSlugResolver.resolveForUpdate(movie("mai"), "mai-2026"));
    }

    @Test
    void updateRejectsSlugOfAnotherMovie() {
        when(movieRepository.existsBySlugAndIdNot("khac", MOVIE_ID)).thenReturn(true);

        AppException appException =
                assertThrows(AppException.class, () -> movieSlugResolver.resolveForUpdate(movie("mai"), "khac"));

        assertEquals("MOVIE_SLUG_EXISTS", appException.getErrorCode());
    }

    private Movie movie(String slug) {
        Movie movie = new Movie();
        movie.setId(MOVIE_ID);
        movie.setSlug(slug);
        return movie;
    }
}
