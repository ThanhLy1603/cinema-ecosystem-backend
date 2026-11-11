package com.cinema.modules.booking.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.util.BookingWindow;
import com.cinema.config.properties.BookingPolicyProperties;
import com.cinema.modules.booking.dto.response.ShowtimeSeatMapResponse;
import com.cinema.modules.booking.dto.response.ShowtimeSeatResponse;
import com.cinema.modules.booking.enums.SeatAvailability;
import com.cinema.modules.booking.service.interfaces.IShowtimeSeatMapService;
import com.cinema.modules.cinema.dto.response.SeatLayoutResponse;
import com.cinema.modules.cinema.dto.response.SeatLayoutSeatResponse;
import com.cinema.modules.cinema.enums.SeatStatus;
import com.cinema.modules.cinema.service.interfaces.ICinemaPublicService;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import com.cinema.modules.showtime.dto.response.SeatTypePriceResponse;
import com.cinema.modules.showtime.dto.response.ShowtimeInfoResponse;
import com.cinema.modules.showtime.dto.response.ShowtimePriceResponse;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import com.cinema.modules.showtime.service.interfaces.IDynamicPricingService;
import com.cinema.modules.showtime.service.interfaces.IShowtimeQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShowtimeSeatMapService implements IShowtimeSeatMapService {

    private final IShowtimeQueryService showtimeQueryService;
    private final ICinemaPublicService cinemaPublicService;
    private final ICinemaQueryService cinemaQueryService;
    private final IMovieQueryService movieQueryService;
    private final IDynamicPricingService dynamicPricingService;
    private final SeatOccupancyReader seatOccupancyReader;
    private final BookingPolicyProperties bookingPolicyProperties;
    private final Clock clock;

    public ShowtimeSeatMapService(
            IShowtimeQueryService showtimeQueryService,
            ICinemaPublicService cinemaPublicService,
            ICinemaQueryService cinemaQueryService,
            IMovieQueryService movieQueryService,
            IDynamicPricingService dynamicPricingService,
            SeatOccupancyReader seatOccupancyReader,
            BookingPolicyProperties bookingPolicyProperties,
            Clock clock) {
        this.showtimeQueryService = showtimeQueryService;
        this.cinemaPublicService = cinemaPublicService;
        this.cinemaQueryService = cinemaQueryService;
        this.movieQueryService = movieQueryService;
        this.dynamicPricingService = dynamicPricingService;
        this.seatOccupancyReader = seatOccupancyReader;
        this.bookingPolicyProperties = bookingPolicyProperties;
        this.clock = clock;
    }

    @Override
    public ShowtimeSeatMapResponse getSeatMap(Long showtimeId) {
        Instant now = Instant.now(clock);
        ShowtimeInfoResponse showtimeInfoResponse = showtimeQueryService.findShowtimeInfo(showtimeId)
                .filter(foundShowtimeInfo -> foundShowtimeInfo.status() == ShowtimeStatus.ACTIVE)
                .orElseThrow(() -> new AppException("Không tìm thấy suất chiếu", HttpStatus.NOT_FOUND, "SHOWTIME_NOT_FOUND"));
        if (!BookingWindow.isOpen(showtimeInfoResponse.startTime(), now, bookingPolicyProperties.lateBookingWindow())) {
            throw new AppException("Đã hết thời gian đặt vé cho suất chiếu này", HttpStatus.CONFLICT, "SHOWTIME_STARTED");
        }
        SeatLayoutResponse seatLayoutResponse = cinemaPublicService.findPublicSeatLayout(showtimeInfoResponse.hallId());
        MovieInfoResponse movieInfoResponse = movieQueryService
                .findMovieInfos(List.of(showtimeInfoResponse.movieId()))
                .get(showtimeInfoResponse.movieId());
        if (movieInfoResponse == null || movieInfoResponse.deleted()) {
            throw new AppException("Không tìm thấy phim", HttpStatus.NOT_FOUND, "MOVIE_NOT_FOUND");
        }
        Map<String, BigDecimal> seatPricesByType = findSeatPricesByType(showtimeId);
        Map<Long, SeatAvailability> occupiedSeats = seatOccupancyReader.findOccupiedSeats(showtimeId, now);
        List<ShowtimeSeatResponse> showtimeSeatResponses = seatLayoutResponse.seats().stream()
                .map(seatLayoutSeatResponse -> toShowtimeSeat(
                        seatLayoutSeatResponse, showtimeInfoResponse, seatPricesByType, occupiedSeats))
                .toList();
        String cinemaName = cinemaQueryService.findCinemaNames(List.of(seatLayoutResponse.cinemaId()))
                .get(seatLayoutResponse.cinemaId());
        return new ShowtimeSeatMapResponse(
                showtimeId,
                movieInfoResponse.id(),
                movieInfoResponse.slug(),
                movieInfoResponse.title(),
                movieInfoResponse.posterUrl(),
                movieInfoResponse.rated(),
                movieInfoResponse.durationMinutes(),
                seatLayoutResponse.cinemaId(),
                cinemaName,
                seatLayoutResponse.hallId(),
                seatLayoutResponse.hallName(),
                seatLayoutResponse.screenType(),
                showtimeInfoResponse.startTime(),
                showtimeInfoResponse.endTime(),
                now,
                seatLayoutResponse.rows(),
                seatLayoutResponse.columns(),
                showtimeSeatResponses);
    }

    private Map<String, BigDecimal> findSeatPricesByType(Long showtimeId) {
        ShowtimePriceResponse showtimePriceResponse = dynamicPricingService.getShowtimePrices(showtimeId);
        return showtimePriceResponse.seatTypePrices().stream()
                .collect(Collectors.toMap(SeatTypePriceResponse::seatTypeCode, SeatTypePriceResponse::totalPrice));
    }

    private ShowtimeSeatResponse toShowtimeSeat(
            SeatLayoutSeatResponse seatLayoutSeatResponse,
            ShowtimeInfoResponse showtimeInfoResponse,
            Map<String, BigDecimal> seatPricesByType,
            Map<Long, SeatAvailability> occupiedSeats) {
        SeatAvailability seatAvailability = seatLayoutSeatResponse.status() == SeatStatus.MAINTENANCE
                ? SeatAvailability.UNAVAILABLE
                : occupiedSeats.getOrDefault(seatLayoutSeatResponse.id(), SeatAvailability.AVAILABLE);
        BigDecimal price = seatPricesByType.getOrDefault(
                seatLayoutSeatResponse.type(),
                showtimeInfoResponse.basePrice().add(seatLayoutSeatResponse.surcharge()));
        return new ShowtimeSeatResponse(
                seatLayoutSeatResponse.id(),
                seatLayoutSeatResponse.row(),
                seatLayoutSeatResponse.number(),
                seatLayoutSeatResponse.type(),
                seatLayoutSeatResponse.span(),
                price,
                seatAvailability);
    }
}
