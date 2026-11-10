package com.cinema.modules.movie.service.interfaces;

import com.cinema.modules.movie.dto.request.GenreRequest;
import com.cinema.modules.movie.dto.response.GenreResponse;

import java.util.List;

public interface IGenreAdminService {

    List<GenreResponse> listGenres();

    GenreResponse createGenre(GenreRequest genreRequest);

    GenreResponse updateGenre(Long genreId, GenreRequest genreRequest);

    void deleteGenre(Long genreId);
}
