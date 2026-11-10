package com.cinema.modules.movie.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.movie.dto.request.GenreRequest;
import com.cinema.modules.movie.dto.response.GenreResponse;
import com.cinema.modules.movie.service.interfaces.IGenreAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/genres")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
public class AdminGenreController {

    private final IGenreAdminService genreAdminService;

    public AdminGenreController(IGenreAdminService genreAdminService) {
        this.genreAdminService = genreAdminService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GenreResponse>>> listGenres() {
        return ResponseEntity.ok(ApiResponse.success(genreAdminService.listGenres()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GenreResponse>> createGenre(@Valid @RequestBody GenreRequest genreRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm thể loại thành công", genreAdminService.createGenre(genreRequest)));
    }

    @PutMapping("/{genreId}")
    public ResponseEntity<ApiResponse<GenreResponse>> updateGenre(
            @PathVariable Long genreId, @Valid @RequestBody GenreRequest genreRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật thể loại thành công", genreAdminService.updateGenre(genreId, genreRequest)));
    }

    @DeleteMapping("/{genreId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteGenre(@PathVariable Long genreId) {
        genreAdminService.deleteGenre(genreId);
        return ResponseEntity.ok(ApiResponse.success("Xóa thể loại thành công", null));
    }
}
