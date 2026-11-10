package com.cinema.modules.showtime.exception;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.showtime.dto.response.ShowtimeConflictResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ShowtimeExceptionHandler {

    @ExceptionHandler(ShowtimeConflictException.class)
    public ResponseEntity<ApiResponse<List<ShowtimeConflictResponse>>> handleShowtimeConflict(
            ShowtimeConflictException showtimeConflictException) {
        ApiResponse<List<ShowtimeConflictResponse>> apiResponse = ApiResponse.<List<ShowtimeConflictResponse>>builder()
                .success(false)
                .message(showtimeConflictException.getMessage())
                .data(showtimeConflictException.getConflicts())
                .errorCode(showtimeConflictException.getErrorCode())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(showtimeConflictException.getStatus()).body(apiResponse);
    }
}
