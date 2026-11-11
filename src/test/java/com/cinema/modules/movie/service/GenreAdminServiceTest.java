package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.movie.dto.request.GenreRequest;
import com.cinema.modules.movie.dto.response.GenreResponse;
import com.cinema.modules.movie.entity.Genre;
import com.cinema.modules.movie.repository.GenreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenreAdminServiceTest {

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private PublicMovieCache publicMovieCache;

    private GenreAdminService genreAdminService;

    @BeforeEach
    void setUp() {
        genreAdminService = new GenreAdminService(genreRepository, publicMovieCache);
    }

    private void stubSave() {
        when(genreRepository.saveAndFlush(any(Genre.class))).thenAnswer(invocation -> {
            Genre genre = invocation.getArgument(0);
            genre.setId(1L);
            return genre;
        });
    }

    @Test
    void createGeneratesAccentFreeSlugAndAddsSuffixWhenTaken() {
        stubSave();
        when(genreRepository.existsBySlug("khoa-hoc-vien-tuong")).thenReturn(true);

        GenreResponse genreResponse = genreAdminService.createGenre(new GenreRequest("  Khoa Học Viễn Tưởng "));

        assertEquals("Khoa Học Viễn Tưởng", genreResponse.name());
        assertEquals("khoa-hoc-vien-tuong-2", genreResponse.slug());
        verify(publicMovieCache).evictAfterCommit();
    }

    @Test
    void createRejectsDuplicateNameIgnoringCase() {
        when(genreRepository.existsByNameIgnoreCase("Kinh Dị")).thenReturn(true);

        AppException appException =
                assertThrows(AppException.class, () -> genreAdminService.createGenre(new GenreRequest("Kinh Dị")));

        assertEquals("GENRE_NAME_EXISTS", appException.getErrorCode());
        verify(genreRepository, never()).saveAndFlush(any());
        verify(publicMovieCache, never()).evictAfterCommit();
    }

    @Test
    void updateKeepsSlugWhenNameIsUnchanged() {
        Genre genre = new Genre();
        genre.setId(1L);
        genre.setName("Hoạt Hình");
        genre.setSlug("hoat-hinh");
        when(genreRepository.findById(1L)).thenReturn(Optional.of(genre));
        when(genreRepository.saveAndFlush(genre)).thenReturn(genre);

        GenreResponse genreResponse = genreAdminService.updateGenre(1L, new GenreRequest("Hoạt Hình"));

        assertEquals("hoat-hinh", genreResponse.slug());
    }

    @Test
    void deleteIsBlockedWhenGenreIsUsedByMovies() {
        Genre genre = new Genre();
        when(genreRepository.findById(1L)).thenReturn(Optional.of(genre));
        when(genreRepository.countMoviesByGenreId(1L)).thenReturn(3L);

        AppException appException = assertThrows(AppException.class, () -> genreAdminService.deleteGenre(1L));

        assertEquals(HttpStatus.CONFLICT, appException.getStatus());
        assertEquals("GENRE_IN_USE", appException.getErrorCode());
        verify(genreRepository, never()).delete(any());
    }

    @Test
    void deleteRemovesUnusedGenre() {
        Genre genre = new Genre();
        when(genreRepository.findById(1L)).thenReturn(Optional.of(genre));
        when(genreRepository.countMoviesByGenreId(1L)).thenReturn(0L);

        genreAdminService.deleteGenre(1L);

        verify(genreRepository).delete(genre);
    }
}
