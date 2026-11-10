package com.cinema.modules.movie.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.movie.dto.request.PersonRequest;
import com.cinema.modules.movie.dto.response.PersonOptionResponse;
import com.cinema.modules.movie.dto.response.PersonResponse;
import com.cinema.modules.movie.service.interfaces.IPersonAdminService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/persons")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
public class AdminPersonController {

    private final IPersonAdminService personAdminService;

    public AdminPersonController(IPersonAdminService personAdminService) {
        this.personAdminService = personAdminService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PersonResponse>>> searchPersons(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(personAdminService.searchPersons(keyword, page, size)));
    }

    @GetMapping("/options")
    public ResponseEntity<ApiResponse<List<PersonOptionResponse>>> listPersonOptions() {
        return ResponseEntity.ok(ApiResponse.success(personAdminService.listPersonOptions()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PersonResponse>> createPerson(@Valid @RequestBody PersonRequest personRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm nghệ sĩ thành công", personAdminService.createPerson(personRequest)));
    }

    @PutMapping("/{personId}")
    public ResponseEntity<ApiResponse<PersonResponse>> updatePerson(
            @PathVariable Long personId, @Valid @RequestBody PersonRequest personRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật nghệ sĩ thành công", personAdminService.updatePerson(personId, personRequest)));
    }

    @DeleteMapping("/{personId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePerson(@PathVariable Long personId) {
        personAdminService.deletePerson(personId);
        return ResponseEntity.ok(ApiResponse.success("Xóa nghệ sĩ thành công", null));
    }
}
