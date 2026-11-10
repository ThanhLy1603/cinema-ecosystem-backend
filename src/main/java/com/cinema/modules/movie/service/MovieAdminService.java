package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.movie.dto.request.MovieCastRequest;
import com.cinema.modules.movie.dto.request.MovieRequest;
import com.cinema.modules.movie.dto.response.MovieDetailResponse;
import com.cinema.modules.movie.dto.response.MovieResponse;
import com.cinema.modules.movie.entity.Genre;
import com.cinema.modules.movie.entity.Movie;
import com.cinema.modules.movie.entity.MovieCast;
import com.cinema.modules.movie.entity.Person;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.repository.GenreRepository;
import com.cinema.modules.movie.repository.MovieCastRepository;
import com.cinema.modules.movie.repository.MovieRepository;
import com.cinema.modules.movie.repository.PersonRepository;
import com.cinema.modules.movie.service.interfaces.IMovieAdminService;
import com.cinema.modules.movie.specification.MovieSpecifications;
import com.cinema.modules.movie.util.MovieMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MovieAdminService implements IMovieAdminService {

    private final MovieRepository movieRepository;
    private final MovieCastRepository movieCastRepository;
    private final GenreRepository genreRepository;
    private final PersonRepository personRepository;
    private final MovieViewAssembler movieViewAssembler;
    private final MovieUsageChecker movieUsageChecker;
    private final MovieSlugResolver movieSlugResolver;
    private final Clock clock;

    public MovieAdminService(
            MovieRepository movieRepository,
            MovieCastRepository movieCastRepository,
            GenreRepository genreRepository,
            PersonRepository personRepository,
            MovieViewAssembler movieViewAssembler,
            MovieUsageChecker movieUsageChecker,
            MovieSlugResolver movieSlugResolver,
            Clock clock) {
        this.movieRepository = movieRepository;
        this.movieCastRepository = movieCastRepository;
        this.genreRepository = genreRepository;
        this.personRepository = personRepository;
        this.movieViewAssembler = movieViewAssembler;
        this.movieUsageChecker = movieUsageChecker;
        this.movieSlugResolver = movieSlugResolver;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<MovieResponse> searchMovies(
            String keyword, Long genreId, MovieStatus status, AgeRating rated, boolean deleted, int page, int size) {
        return movieViewAssembler.searchMovies(
                MovieSpecifications.search(keyword, genreId, status, rated, deleted), page, size);
    }

    @Transactional(readOnly = true)
    @Override
    public MovieDetailResponse getMovie(Long movieId) {
        return movieViewAssembler.getMovieDetail(movieId);
    }

    @Transactional
    @Override
    public MovieDetailResponse createMovie(MovieRequest movieRequest) {
        validateDates(movieRequest);
        Movie movie = new Movie();
        applyRequest(movie, movieRequest);
        movie.setSlug(movieSlugResolver.resolveForCreate(movieRequest.slug(), movie.getTitle()));
        Movie savedMovie = saveMovie(movie);
        List<MovieCast> movieCasts = replaceCasts(savedMovie.getId(), movieRequest.casts());
        return MovieMapper.toMovieDetailResponse(savedMovie, movieCasts);
    }

    @Transactional
    @Override
    public MovieDetailResponse updateMovie(Long movieId, MovieRequest movieRequest) {
        validateDates(movieRequest);
        Movie movie = movieViewAssembler.loadMovieWithGenres(movieId, false);
        assertScheduledShowtimesStayConsistent(movie, movieRequest);
        applyRequest(movie, movieRequest);
        movie.setSlug(movieSlugResolver.resolveForUpdate(movie, movieRequest.slug()));
        Movie savedMovie = saveMovie(movie);
        List<MovieCast> movieCasts = replaceCasts(movieId, movieRequest.casts());
        return MovieMapper.toMovieDetailResponse(savedMovie, movieCasts);
    }

    @Transactional
    @Override
    public void deleteMovie(Long movieId) {
        Movie movie = movieViewAssembler.loadMovieWithGenres(movieId, false);
        Instant now = Instant.now(clock);
        if (movieUsageChecker.countUpcomingShowtimesByMovie(movieId, now) > 0) {
            throw new AppException(
                    "Phim còn suất chiếu chưa diễn ra, hãy hủy các suất chiếu trước khi xóa phim",
                    HttpStatus.CONFLICT,
                    "MOVIE_HAS_UPCOMING_SHOWTIMES");
        }
        movie.markDeleted(now);
        movieRepository.save(movie);
    }

    @Transactional
    @Override
    public MovieDetailResponse restoreMovie(Long movieId) {
        Movie movie = movieViewAssembler.loadMovieWithGenres(movieId, true);
        movie.setSlug(movieSlugResolver.resolveForRestore(movie));
        movie.restore();
        saveMovie(movie);
        return movieViewAssembler.getMovieDetail(movieId);
    }

    private void assertScheduledShowtimesStayConsistent(Movie movie, MovieRequest movieRequest) {
        boolean durationChanged = movie.getDurationMinutes() != movieRequest.durationMinutes();
        boolean becomingEnded = movieRequest.status() == MovieStatus.ENDED && movie.getStatus() != MovieStatus.ENDED;
        if (!durationChanged && !becomingEnded) {
            return;
        }
        if (movieUsageChecker.countUpcomingShowtimesByMovie(movie.getId(), Instant.now(clock)) == 0) {
            return;
        }
        if (durationChanged) {
            throw new AppException(
                    "Phim còn suất chiếu chưa diễn ra, hãy hủy các suất chiếu trước khi đổi thời lượng",
                    HttpStatus.CONFLICT,
                    "MOVIE_DURATION_LOCKED");
        }
        throw new AppException(
                "Phim còn suất chiếu chưa diễn ra, hãy hủy các suất chiếu trước khi chuyển sang ngừng chiếu",
                HttpStatus.CONFLICT,
                "MOVIE_HAS_UPCOMING_SHOWTIMES");
    }

    private void validateDates(MovieRequest movieRequest) {
        if (movieRequest.endDate() != null && movieRequest.endDate().isBefore(movieRequest.releaseDate())) {
            throw new AppException(
                    "Ngày kết thúc chiếu phải sau hoặc bằng ngày khởi chiếu", HttpStatus.BAD_REQUEST, "INVALID_MOVIE_DATES");
        }
    }

    private void applyRequest(Movie movie, MovieRequest movieRequest) {
        movie.setTitle(movieRequest.title().trim());
        movie.setOriginalTitle(blankToNull(movieRequest.originalTitle()));
        movie.setDurationMinutes(movieRequest.durationMinutes());
        movie.setReleaseDate(movieRequest.releaseDate());
        movie.setEndDate(movieRequest.endDate());
        movie.setRated(movieRequest.rated());
        movie.setPosterUrl(blankToNull(movieRequest.posterUrl()));
        movie.setBannerUrl(blankToNull(movieRequest.bannerUrl()));
        movie.setTrailerUrl(blankToNull(movieRequest.trailerUrl()));
        movie.setDescription(blankToNull(movieRequest.description()));
        movie.setStatus(movieRequest.status());
        movie.setGenres(resolveGenres(movieRequest.genreIds()));
    }

    private Set<Genre> resolveGenres(List<Long> genreIds) {
        if (genreIds == null || genreIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Long> distinctGenreIds = new HashSet<>(genreIds);
        List<Genre> genres = genreRepository.findAllByIdIn(distinctGenreIds);
        if (genres.size() != distinctGenreIds.size()) {
            throw new AppException("Có thể loại không tồn tại", HttpStatus.BAD_REQUEST, "INVALID_GENRE");
        }
        return new HashSet<>(genres);
    }

    private List<MovieCast> replaceCasts(Long movieId, List<MovieCastRequest> movieCastRequests) {
        movieCastRepository.deleteAllByMovieId(movieId);
        if (movieCastRequests == null || movieCastRequests.isEmpty()) {
            return List.of();
        }
        Set<String> castKeys = new HashSet<>();
        for (MovieCastRequest movieCastRequest : movieCastRequests) {
            if (!castKeys.add(movieCastRequest.personId() + ":" + movieCastRequest.roleType())) {
                throw new AppException(
                        "Một nghệ sĩ không thể giữ cùng một vai trò hai lần trong phim",
                        HttpStatus.BAD_REQUEST,
                        "DUPLICATE_CAST");
            }
        }
        Set<Long> personIds = movieCastRequests.stream().map(MovieCastRequest::personId).collect(Collectors.toSet());
        Map<Long, Person> persons = personRepository.findAllByIdIn(personIds).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
        if (persons.size() != personIds.size()) {
            throw new AppException("Có nghệ sĩ không tồn tại", HttpStatus.BAD_REQUEST, "INVALID_PERSON");
        }
        List<MovieCast> movieCasts = new ArrayList<>();
        for (MovieCastRequest movieCastRequest : movieCastRequests) {
            MovieCast movieCast = new MovieCast();
            movieCast.setMovieId(movieId);
            movieCast.setPerson(persons.get(movieCastRequest.personId()));
            movieCast.setRoleType(movieCastRequest.roleType());
            movieCast.setCharacterName(blankToNull(movieCastRequest.characterName()));
            movieCasts.add(movieCast);
        }
        return movieCastRepository.saveAll(movieCasts);
    }

    private Movie saveMovie(Movie movie) {
        try {
            return movieRepository.saveAndFlush(movie);
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            throw new AppException(
                    "Dữ liệu phim không hợp lệ hoặc đường dẫn phim vừa bị dùng, vui lòng thử lại",
                    HttpStatus.CONFLICT,
                    "MOVIE_CONFLICT");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
