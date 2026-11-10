package com.cinema.modules.showtime.exception;

import com.cinema.common.exception.AppException;
import com.cinema.modules.showtime.dto.response.ShowtimeConflictResponse;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
public class ShowtimeConflictException extends AppException {

    public static final String ERROR_CODE = "SHOWTIME_CONFLICT";

    private final List<ShowtimeConflictResponse> conflicts;

    public ShowtimeConflictException(List<ShowtimeConflictResponse> conflicts) {
        super("Phòng chiếu đang bận hoặc chưa đủ 15 phút dọn phòng giữa hai suất chiếu", HttpStatus.CONFLICT, ERROR_CODE);
        this.conflicts = List.copyOf(conflicts);
    }
}
