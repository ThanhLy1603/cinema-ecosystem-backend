package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.util.SlugGenerator;
import com.cinema.modules.movie.dto.request.GenreRequest;
import com.cinema.modules.movie.dto.response.GenreResponse;
import com.cinema.modules.movie.entity.Genre;
import com.cinema.modules.movie.repository.GenreRepository;
import com.cinema.modules.movie.service.interfaces.IGenreAdminService;
import com.cinema.modules.movie.util.MovieMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GenreAdminService implements IGenreAdminService {

    private static final int MAX_SLUG_ATTEMPTS = 50;

    private final GenreRepository genreRepository;

    public GenreAdminService(GenreRepository genreRepository) {
        this.genreRepository = genreRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public List<GenreResponse> listGenres() {
        return genreRepository.findAllByOrderByNameAsc().stream().map(MovieMapper::toGenreResponse).toList();
    }

    @Transactional
    @Override
    public GenreResponse createGenre(GenreRequest genreRequest) {
        String name = genreRequest.name().trim();
        if (genreRepository.existsByNameIgnoreCase(name)) {
            throw nameAlreadyUsed();
        }
        Genre genre = new Genre();
        genre.setName(name);
        genre.setSlug(generateUniqueSlug(name));
        return saveGenre(genre);
    }

    @Transactional
    @Override
    public GenreResponse updateGenre(Long genreId, GenreRequest genreRequest) {
        Genre genre = loadGenre(genreId);
        String name = genreRequest.name().trim();
        if (genreRepository.existsByNameIgnoreCaseAndIdNot(name, genreId)) {
            throw nameAlreadyUsed();
        }
        if (!genre.getName().equals(name)) {
            genre.setName(name);
            genre.setSlug(generateUniqueSlug(name));
        }
        return saveGenre(genre);
    }

    @Transactional
    @Override
    public void deleteGenre(Long genreId) {
        Genre genre = loadGenre(genreId);
        if (genreRepository.countMoviesByGenreId(genreId) > 0) {
            throw new AppException(
                    "Thể loại đang được gán cho phim, hãy gỡ khỏi các phim trước khi xóa",
                    HttpStatus.CONFLICT,
                    "GENRE_IN_USE");
        }
        genreRepository.delete(genre);
    }

    private Genre loadGenre(Long genreId) {
        return genreRepository.findById(genreId)
                .orElseThrow(() -> new AppException("Không tìm thấy thể loại", HttpStatus.NOT_FOUND, "GENRE_NOT_FOUND"));
    }

    private GenreResponse saveGenre(Genre genre) {
        try {
            return MovieMapper.toGenreResponse(genreRepository.saveAndFlush(genre));
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            throw nameAlreadyUsed();
        }
    }

    private String generateUniqueSlug(String name) {
        String baseSlug = SlugGenerator.toSlug(name);
        String slug = baseSlug;
        for (int attempt = 2; genreRepository.existsBySlug(slug); attempt++) {
            if (attempt > MAX_SLUG_ATTEMPTS) {
                throw new AppException("Không tạo được đường dẫn cho thể loại", HttpStatus.CONFLICT, "SLUG_EXHAUSTED");
            }
            slug = baseSlug + "-" + attempt;
        }
        return slug;
    }

    private static AppException nameAlreadyUsed() {
        return new AppException("Tên thể loại đã tồn tại", HttpStatus.CONFLICT, "GENRE_NAME_EXISTS");
    }
}
