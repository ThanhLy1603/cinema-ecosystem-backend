package com.cinema.modules.movie.service;

import com.cinema.common.response.PageResponse;
import com.cinema.modules.movie.dto.response.GenreResponse;
import com.cinema.modules.movie.dto.response.MovieDetailResponse;
import com.cinema.modules.movie.dto.response.MovieResponse;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.repository.GenreRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MoviePublicServiceTest {

    @Mock
    private MovieViewAssembler movieViewAssembler;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private PublicMovieCache publicMovieCache;

    private MoviePublicService moviePublicService;

    @BeforeEach
    void setUp() {
        moviePublicService = new MoviePublicService(movieViewAssembler, genreRepository, publicMovieCache);
        lenient().when(publicMovieCache.getOrLoad(anyString(), any(TypeReference.class), any(Supplier.class)))
                .thenAnswer(invocationOnMock -> ((Supplier<?>) invocationOnMock.getArgument(2)).get());
    }

    @Test
    void listingWithoutKeywordIsCachedUnderAKeyThatIncludesEveryFilter() {
        PageResponse<MovieResponse> emptyPage = new PageResponse<>(List.of(), 0, 12, 0, 0);
        when(movieViewAssembler.searchMovies(any(Specification.class), eq(0), eq(12))).thenReturn(emptyPage);

        PageResponse<MovieResponse> pageResponse =
                moviePublicService.searchMovies(null, 3L, MovieStatus.NOW_SHOWING, AgeRating.C13, 0, 12);

        assertEquals(emptyPage, pageResponse);
        ArgumentCaptor<String> cacheNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(publicMovieCache).getOrLoad(cacheNameCaptor.capture(), any(TypeReference.class), any(Supplier.class));
        assertEquals("list:NOW_SHOWING:3:C13:0:12", cacheNameCaptor.getValue());
    }

    @Test
    void blankKeywordIsTreatedAsNoKeywordAndStillCached() {
        when(movieViewAssembler.searchMovies(any(Specification.class), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 12, 0, 0));

        moviePublicService.searchMovies("   ", null, MovieStatus.COMING_SOON, null, 1, 12);

        verify(publicMovieCache).getOrLoad(eq("list:COMING_SOON:null:null:1:12"), any(TypeReference.class), any(Supplier.class));
    }

    @Test
    void keywordSearchBypassesTheCacheToAvoidUnboundedKeys() {
        when(movieViewAssembler.searchMovies(any(Specification.class), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 12, 0, 0));

        moviePublicService.searchMovies("mai", null, MovieStatus.NOW_SHOWING, null, 0, 12);

        verify(publicMovieCache, never()).getOrLoad(anyString(), any(TypeReference.class), any(Supplier.class));
        verify(movieViewAssembler).searchMovies(any(Specification.class), eq(0), eq(12));
    }

    @Test
    void detailIsCachedBySlug() {
        MovieDetailResponse movieDetailResponse = org.mockito.Mockito.mock(MovieDetailResponse.class);
        when(movieViewAssembler.getMovieDetailBySlug("mai")).thenReturn(movieDetailResponse);

        assertEquals(movieDetailResponse, moviePublicService.getMovieBySlug("mai"));

        verify(publicMovieCache).getOrLoad(eq("detail:mai"), any(TypeReference.class), any(Supplier.class));
    }

    @Test
    void genresAreCached() {
        when(genreRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        List<GenreResponse> genreResponses = moviePublicService.findGenres();

        assertEquals(List.of(), genreResponses);
        verify(publicMovieCache).getOrLoad(eq("genres"), any(TypeReference.class), any(Supplier.class));
    }
}
