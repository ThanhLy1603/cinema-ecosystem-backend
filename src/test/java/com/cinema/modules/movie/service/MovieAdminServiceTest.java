package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.movie.dto.request.MovieCastRequest;
import com.cinema.modules.movie.dto.request.MovieRequest;
import com.cinema.modules.movie.dto.response.MovieDetailResponse;
import com.cinema.modules.movie.entity.Genre;
import com.cinema.modules.movie.entity.Movie;
import com.cinema.modules.movie.entity.MovieCast;
import com.cinema.modules.movie.entity.Person;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.CastRoleType;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.repository.GenreRepository;
import com.cinema.modules.movie.repository.MovieCastRepository;
import com.cinema.modules.movie.repository.MovieRepository;
import com.cinema.modules.movie.repository.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieAdminServiceTest {

    private static final Long MOVIE_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private MovieCastRepository movieCastRepository;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private MovieViewAssembler movieViewAssembler;

    @Mock
    private MovieUsageChecker movieUsageChecker;

    @Mock
    private MovieSlugResolver movieSlugResolver;

    @Mock
    private PublicMovieCache publicMovieCache;

    private MovieAdminService movieAdminService;
    private Genre genre;
    private Person person;

    @BeforeEach
    void setUp() {
        movieAdminService = new MovieAdminService(
                movieRepository,
                movieCastRepository,
                genreRepository,
                personRepository,
                movieViewAssembler,
                movieUsageChecker,
                movieSlugResolver,
                publicMovieCache,
                Clock.fixed(NOW, ZoneOffset.UTC));
        genre = new Genre();
        genre.setId(1L);
        genre.setName("Hành Động");
        genre.setSlug("hanh-dong");
        person = new Person();
        person.setId(5L);
        person.setName("Trấn Thành");
    }

    private MovieRequest movieRequest(LocalDate endDate, List<Long> genreIds, List<MovieCastRequest> casts) {
        return movieRequest("  Mai  ", null, endDate, genreIds, casts);
    }

    private MovieRequest movieRequest(
            String title, String slug, LocalDate endDate, List<Long> genreIds, List<MovieCastRequest> casts) {
        return new MovieRequest(
                title, slug, " ", 131, LocalDate.of(2026, 2, 10), endDate, AgeRating.C16, null, "", null,
                "Mô tả", MovieStatus.NOW_SHOWING, genreIds, casts);
    }

    private void stubSave() {
        when(movieRepository.saveAndFlush(any(Movie.class))).thenAnswer(invocation -> {
            Movie movie = invocation.getArgument(0);
            movie.setId(MOVIE_ID);
            return movie;
        });
    }

    @Test
    void createTrimsTextAndStoresGenresAndCasts() {
        stubSave();
        when(genreRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(genre));
        when(personRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(person));
        when(movieCastRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MovieDetailResponse movieDetailResponse = movieAdminService.createMovie(movieRequest(
                null, List.of(1L), List.of(new MovieCastRequest(5L, CastRoleType.ACTOR, " Mai ")))); 

        assertEquals("Mai", movieDetailResponse.title());
        assertEquals(null, movieDetailResponse.originalTitle());
        assertEquals(null, movieDetailResponse.posterUrl());
        assertEquals(AgeRating.C16, movieDetailResponse.rated());
        assertEquals("hanh-dong", movieDetailResponse.genres().get(0).slug());
        assertEquals("Trấn Thành", movieDetailResponse.casts().get(0).personName());
        assertEquals("Mai", movieDetailResponse.casts().get(0).characterName());
        verify(movieCastRepository).deleteAllByMovieId(MOVIE_ID);
    }

    @Test
    void failedValidationDoesNotTouchThePublicCache() {
        AppException appException = assertThrows(
                AppException.class,
                () -> movieAdminService.createMovie(
                        movieRequest(LocalDate.of(2020, 1, 1), List.of(1L), List.of())));

        assertNotNull(appException);
        verify(publicMovieCache, org.mockito.Mockito.never()).evictAfterCommit();
    }

    @Test
    void createRejectsEndDateBeforeReleaseDate() {
        AppException appException = assertThrows(AppException.class, () -> movieAdminService.createMovie(
                movieRequest(LocalDate.of(2026, 2, 9), List.of(), List.of())));

        assertEquals("INVALID_MOVIE_DATES", appException.getErrorCode());
        verify(movieRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsUnknownGenre() {
        when(genreRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());

        AppException appException = assertThrows(AppException.class, () -> movieAdminService.createMovie(
                movieRequest(null, List.of(99L), List.of())));

        assertEquals("INVALID_GENRE", appException.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, appException.getStatus());
    }

    @Test
    void createRejectsSamePersonWithSameRoleTwice() {
        stubSave();
        List<MovieCastRequest> movieCastRequests = List.of(
                new MovieCastRequest(5L, CastRoleType.ACTOR, null), new MovieCastRequest(5L, CastRoleType.ACTOR, "Khác"));

        AppException appException = assertThrows(AppException.class, () -> movieAdminService.createMovie(
                movieRequest(null, List.of(), movieCastRequests)));

        assertEquals("DUPLICATE_CAST", appException.getErrorCode());
        verify(movieCastRepository, never()).saveAll(any());
    }

    @Test
    void createAllowsSamePersonWithDifferentRoles() {
        stubSave();
        when(personRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(person));
        when(movieCastRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MovieDetailResponse movieDetailResponse = movieAdminService.createMovie(movieRequest(null, List.of(), List.of(
                new MovieCastRequest(5L, CastRoleType.ACTOR, null), new MovieCastRequest(5L, CastRoleType.DIRECTOR, null))));

        assertEquals(2, movieDetailResponse.casts().size());
    }

    @Test
    void createRejectsUnknownPerson() {
        stubSave();
        when(personRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());

        AppException appException = assertThrows(AppException.class, () -> movieAdminService.createMovie(
                movieRequest(null, List.of(), List.of(new MovieCastRequest(5L, CastRoleType.ACTOR, null)))));

        assertEquals("INVALID_PERSON", appException.getErrorCode());
    }

    @Test
    void updateReplacesCastsOfExistingMovie() {
        Movie movie = new Movie();
        movie.setId(MOVIE_ID);
        movie.setGenres(Set.of());
        when(movieViewAssembler.loadMovieWithGenres(MOVIE_ID, false)).thenReturn(movie);
        when(movieRepository.saveAndFlush(movie)).thenReturn(movie);

        MovieDetailResponse movieDetailResponse =
                movieAdminService.updateMovie(MOVIE_ID, movieRequest(null, List.of(), List.of()));

        assertEquals("Mai", movie.getTitle());
        assertTrue(movieDetailResponse.casts().isEmpty());
        verify(movieCastRepository).deleteAllByMovieId(MOVIE_ID);
    }

    @Test
    void createStoresSlugFromResolver() {
        stubSave();
        when(movieSlugResolver.resolveForCreate("mai-2026", "Mai")).thenReturn("mai-2026");

        MovieDetailResponse movieDetailResponse = movieAdminService.createMovie(
                movieRequest("  Mai  ", "mai-2026", null, List.of(), List.of()));

        assertEquals("mai-2026", movieDetailResponse.slug());
    }

    @Test
    void createStopsWhenSlugIsAlreadyUsed() {
        when(movieSlugResolver.resolveForCreate("mai", "Mai"))
                .thenThrow(new AppException("Đường dẫn phim đã được dùng cho phim khác", HttpStatus.CONFLICT, "MOVIE_SLUG_EXISTS"));

        AppException appException = assertThrows(AppException.class, () -> movieAdminService.createMovie(
                movieRequest("Mai", "mai", null, List.of(), List.of())));

        assertEquals("MOVIE_SLUG_EXISTS", appException.getErrorCode());
        verify(movieRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateKeepsSlugWhenRequestLeavesItBlank() {
        Movie movie = existingMovie(131, MovieStatus.NOW_SHOWING);
        movie.setSlug("mai");
        when(movieViewAssembler.loadMovieWithGenres(MOVIE_ID, false)).thenReturn(movie);
        when(movieRepository.saveAndFlush(movie)).thenReturn(movie);
        when(movieSlugResolver.resolveForUpdate(movie, null)).thenReturn("mai");

        MovieDetailResponse movieDetailResponse =
                movieAdminService.updateMovie(MOVIE_ID, movieRequest(null, List.of(), List.of()));

        assertEquals("mai", movieDetailResponse.slug());
    }

    @Test
    void updateRejectsDurationChangeWhileUpcomingShowtimesExist() {
        Movie movie = existingMovie(120, MovieStatus.NOW_SHOWING);
        when(movieViewAssembler.loadMovieWithGenres(MOVIE_ID, false)).thenReturn(movie);
        when(movieUsageChecker.countUpcomingShowtimesByMovie(MOVIE_ID, NOW)).thenReturn(3L);

        AppException appException = assertThrows(AppException.class,
                () -> movieAdminService.updateMovie(MOVIE_ID, movieRequest(null, List.of(), List.of())));

        assertEquals(HttpStatus.CONFLICT, appException.getStatus());
        assertEquals("MOVIE_DURATION_LOCKED", appException.getErrorCode());
        assertEquals(120, movie.getDurationMinutes());
        verify(movieRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsEndingMovieWhileUpcomingShowtimesExist() {
        Movie movie = existingMovie(131, MovieStatus.NOW_SHOWING);
        when(movieViewAssembler.loadMovieWithGenres(MOVIE_ID, false)).thenReturn(movie);
        when(movieUsageChecker.countUpcomingShowtimesByMovie(MOVIE_ID, NOW)).thenReturn(1L);
        MovieRequest endedMovieRequest = new MovieRequest(
                "Mai", null, null, 131, LocalDate.of(2026, 2, 10), null, AgeRating.C16, null, null, null, null,
                MovieStatus.ENDED, List.of(), List.of());

        AppException appException =
                assertThrows(AppException.class, () -> movieAdminService.updateMovie(MOVIE_ID, endedMovieRequest));

        assertEquals("MOVIE_HAS_UPCOMING_SHOWTIMES", appException.getErrorCode());
        assertEquals(MovieStatus.NOW_SHOWING, movie.getStatus());
    }

    @Test
    void updateSkipsShowtimeLookupWhenDurationAndStatusAreUnchanged() {
        Movie movie = existingMovie(131, MovieStatus.NOW_SHOWING);
        when(movieViewAssembler.loadMovieWithGenres(MOVIE_ID, false)).thenReturn(movie);
        when(movieRepository.saveAndFlush(movie)).thenReturn(movie);

        movieAdminService.updateMovie(MOVIE_ID, movieRequest(null, List.of(), List.of()));

        verify(movieUsageChecker, never()).countUpcomingShowtimesByMovie(any(), any());
    }

    @Test
    void updateAllowsEndingMovieWithoutUpcomingShowtimes() {
        Movie movie = existingMovie(131, MovieStatus.NOW_SHOWING);
        when(movieViewAssembler.loadMovieWithGenres(MOVIE_ID, false)).thenReturn(movie);
        when(movieRepository.saveAndFlush(movie)).thenReturn(movie);
        when(movieUsageChecker.countUpcomingShowtimesByMovie(MOVIE_ID, NOW)).thenReturn(0L);
        MovieRequest endedMovieRequest = new MovieRequest(
                "Mai", null, null, 131, LocalDate.of(2026, 2, 10), null, AgeRating.C16, null, null, null, null,
                MovieStatus.ENDED, List.of(), List.of());

        movieAdminService.updateMovie(MOVIE_ID, endedMovieRequest);

        assertEquals(MovieStatus.ENDED, movie.getStatus());
    }

    private Movie existingMovie(int durationMinutes, MovieStatus movieStatus) {
        Movie movie = new Movie();
        movie.setId(MOVIE_ID);
        movie.setDurationMinutes(durationMinutes);
        movie.setStatus(movieStatus);
        movie.setGenres(Set.of());
        return movie;
    }

    @Test
    void deleteIsBlockedWhileUpcomingShowtimesExist() {
        Movie movie = new Movie();
        when(movieViewAssembler.loadMovieWithGenres(MOVIE_ID, false)).thenReturn(movie);
        when(movieUsageChecker.countUpcomingShowtimesByMovie(MOVIE_ID, NOW)).thenReturn(2L);

        AppException appException = assertThrows(AppException.class, () -> movieAdminService.deleteMovie(MOVIE_ID));

        assertEquals(HttpStatus.CONFLICT, appException.getStatus());
        assertEquals("MOVIE_HAS_UPCOMING_SHOWTIMES", appException.getErrorCode());
        assertFalse(movie.isDeleted());
        verify(movieRepository, never()).save(any());
    }

    @Test
    void deleteIsBlockedOnceCustomersHaveBookedAnyShowtimeOfTheMovie() {
        Movie movie = new Movie();
        when(movieViewAssembler.loadMovieWithGenres(MOVIE_ID, false)).thenReturn(movie);
        when(movieUsageChecker.countShowtimesWithBookingsByMovie(MOVIE_ID, NOW)).thenReturn(3L);

        AppException appException = assertThrows(AppException.class, () -> movieAdminService.deleteMovie(MOVIE_ID));

        assertEquals("MOVIE_HAS_BOOKINGS", appException.getErrorCode());
        assertFalse(movie.isDeleted());
        verify(movieRepository, org.mockito.Mockito.never()).save(any(Movie.class));
        verify(publicMovieCache, org.mockito.Mockito.never()).evictAfterCommit();
    }

    @Test
    void deleteMarksMovieAsSoftDeleted() {
        Movie movie = new Movie();
        when(movieViewAssembler.loadMovieWithGenres(MOVIE_ID, false)).thenReturn(movie);
        when(movieUsageChecker.countUpcomingShowtimesByMovie(MOVIE_ID, NOW)).thenReturn(0L);

        movieAdminService.deleteMovie(MOVIE_ID);

        assertTrue(movie.isDeleted());
        assertEquals(NOW, movie.getDeletedAt());
        verify(movieRepository).save(movie);
        verify(publicMovieCache).evictAfterCommit();
    }

    @Test
    void restoreClearsDeletedFlag() {
        Movie movie = new Movie();
        movie.markDeleted(NOW);
        when(movieViewAssembler.loadMovieWithGenres(MOVIE_ID, true)).thenReturn(movie);

        movie.setSlug("mai");
        when(movieSlugResolver.resolveForRestore(movie)).thenReturn("mai-2");
        when(movieRepository.saveAndFlush(movie)).thenReturn(movie);

        movieAdminService.restoreMovie(MOVIE_ID);

        assertFalse(movie.isDeleted());
        assertEquals("mai-2", movie.getSlug());
        verify(movieRepository).saveAndFlush(movie);
        verify(publicMovieCache).evictAfterCommit();
        verify(movieViewAssembler).getMovieDetail(MOVIE_ID);
    }

    @Test
    void castEntityKeepsMovieIdAndPerson() {
        stubSave();
        when(personRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(person));
        when(movieCastRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        movieAdminService.createMovie(movieRequest(
                null, List.of(), List.of(new MovieCastRequest(5L, CastRoleType.PRODUCER, null))));

        org.mockito.ArgumentCaptor<List<MovieCast>> movieCastCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(movieCastRepository).saveAll(movieCastCaptor.capture());
        assertEquals(MOVIE_ID, movieCastCaptor.getValue().get(0).getMovieId());
        assertEquals(person, movieCastCaptor.getValue().get(0).getPerson());
        assertEquals(CastRoleType.PRODUCER, movieCastCaptor.getValue().get(0).getRoleType());
        assertEquals(Optional.empty(), Optional.ofNullable(movieCastCaptor.getValue().get(0).getCharacterName()));
    }
}
