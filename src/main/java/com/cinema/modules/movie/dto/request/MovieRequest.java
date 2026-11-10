package com.cinema.modules.movie.dto.request;

import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record MovieRequest(
        @NotBlank(message = "Vui lòng nhập tên phim")
        @Size(max = 255, message = "Tên phim tối đa 255 ký tự")
        String title,

        @Size(max = 255, message = "Tên gốc tối đa 255 ký tự")
        String originalTitle,

        @NotNull(message = "Vui lòng nhập thời lượng")
        @Min(value = 1, message = "Thời lượng tối thiểu 1 phút")
        @Max(value = 600, message = "Thời lượng tối đa 600 phút")
        Integer durationMinutes,

        @NotNull(message = "Vui lòng chọn ngày khởi chiếu")
        LocalDate releaseDate,

        LocalDate endDate,

        @NotNull(message = "Vui lòng chọn nhãn độ tuổi")
        AgeRating rated,

        @Size(max = 500, message = "Đường dẫn poster tối đa 500 ký tự")
        @Pattern(regexp = "^(https://\\S+)?$", message = "Poster phải là đường dẫn bắt đầu bằng https://")
        String posterUrl,

        @Size(max = 500, message = "Đường dẫn banner tối đa 500 ký tự")
        @Pattern(regexp = "^(https://\\S+)?$", message = "Banner phải là đường dẫn bắt đầu bằng https://")
        String bannerUrl,

        @Size(max = 500, message = "Đường dẫn trailer tối đa 500 ký tự")
        @Pattern(regexp = "^(https://\\S+)?$", message = "Trailer phải là đường dẫn bắt đầu bằng https://")
        String trailerUrl,

        @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự")
        String description,

        @NotNull(message = "Vui lòng chọn trạng thái")
        MovieStatus status,

        @Size(max = 10, message = "Mỗi phim tối đa 10 thể loại")
        List<Long> genreIds,

        @Valid
        @Size(max = 60, message = "Mỗi phim tối đa 60 nghệ sĩ")
        List<MovieCastRequest> casts) {
}
