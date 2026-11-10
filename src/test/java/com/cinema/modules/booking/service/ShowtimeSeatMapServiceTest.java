package com.cinema.modules.booking.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.booking.dto.response.ShowtimeSeatMapResponse;
import com.cinema.modules.booking.dto.response.ShowtimeSeatResponse;
import com.cinema.modules.booking.enums.SeatAvailability;
import com.cinema.modules.cinema.dto.response.SeatLayoutResponse;
import com.cinema.modules.cinema.dto.response.SeatLayoutSeatResponse;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.cinema.enums.SeatStatus;
import com.cinema.modules.cinema.service.interfaces.ICinemaPublicService;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.movie.enums.MovieStatus;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import com.cinema.modules.showtime.dto.response.SeatTypePriceResponse;
import com.cinema.modules.showtime.dto.response.ShowtimeInfoResponse;
import com.cinema.modules.showtime.dto.response.ShowtimePriceResponse;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import com.cinema.modules.showtime.service.interfaces.IDynamicPricingService;
import com.cinema.modules.showtime.service.interfaces.IShowtimeQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowtimeSeatMapServiceTest {

    private static final Long SHOWTIME_ID = 5L;
    private static final Long HALL_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");

    @Mock
    private IShowtimeQueryService showtimeQueryService;

    @Mock
    private ICinemaPublicService cinemaPublicService;

    @Mock
    private ICinemaQueryService cinemaQueryService;

    @Mock
    private IMovieQueryService movieQueryService;

    @Mock
    private IDynamicPricingService dynamicPricingService;

    @Mock
    private SeatOccupancyReader seatOccupancyReader;

    private ShowtimeSeatMapService showtimeSeatMapService;

    @BeforeEach
    void setUp() {
        showtimeSeatMapService = new ShowtimeSeatMapService(
                showtimeQueryService,
                cinemaPublicService,
                cinemaQueryService,
                movieQueryService,
                dynamicPricingService,
                seatOccupancyReader,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void unknownShowtimeIsNotFound() {
        when(showtimeQueryService.findShowtimeInfo(SHOWTIME_ID)).thenReturn(Optional.empty());

        AppException appException = assertThrows(AppException.class, () -> showtimeSeatMapService.getSeatMap(SHOWTIME_ID));

        assertEquals("SHOWTIME_NOT_FOUND", appException.getErrorCode());
    }

    @Test
    void cancelledShowtimeIsNotFound() {
        when(showtimeQueryService.findShowtimeInfo(SHOWTIME_ID))
                .thenReturn(Optional.of(showtimeInfo(ShowtimeStatus.CANCELLED, NOW.plusSeconds(3600))));

        AppException appException = assertThrows(AppException.class, () -> showtimeSeatMapService.getSeatMap(SHOWTIME_ID));

        assertEquals("SHOWTIME_NOT_FOUND", appException.getErrorCode());
    }

    @Test
    void startedShowtimeIsRejectedWithConflict() {
        when(showtimeQueryService.findShowtimeInfo(SHOWTIME_ID))
                .thenReturn(Optional.of(showtimeInfo(ShowtimeStatus.ACTIVE, NOW.minusSeconds(60))));

        AppException appException = assertThrows(AppException.class, () -> showtimeSeatMapService.getSeatMap(SHOWTIME_ID));

        assertEquals("SHOWTIME_STARTED", appException.getErrorCode());
    }

    @Test
    void seatStatesAreResolvedPerShowtimeWithDynamicPrices() {
        when(showtimeQueryService.findShowtimeInfo(SHOWTIME_ID))
                .thenReturn(Optional.of(showtimeInfo(ShowtimeStatus.ACTIVE, NOW.plusSeconds(3600))));
        when(cinemaPublicService.findPublicSeatLayout(HALL_ID)).thenReturn(new SeatLayoutResponse(
                HALL_ID, 1L, "Phòng 1", ScreenType.SCREEN_2D, HallStatus.ACTIVE, 3L, List.of("A"), 5, List.of(
                        layoutSeat(1L, 1, "STANDARD", SeatStatus.ACTIVE),
                        layoutSeat(2L, 2, "STANDARD", SeatStatus.ACTIVE),
                        layoutSeat(3L, 3, "STANDARD", SeatStatus.ACTIVE),
                        layoutSeat(4L, 4, "VIP", SeatStatus.ACTIVE),
                        layoutSeat(5L, 5, "VIP", SeatStatus.MAINTENANCE))));
        when(movieQueryService.findMovieInfos(List.of(7L))).thenReturn(Map.of(7L, new MovieInfoResponse(
                7L, "Phim", "phim", 120, AgeRating.C13, "poster.jpg", MovieStatus.NOW_SHOWING, false)));
        when(cinemaQueryService.findCinemaNames(List.of(1L))).thenReturn(Map.of(1L, "Rạp A"));
        when(dynamicPricingService.getShowtimePrices(SHOWTIME_ID)).thenReturn(new ShowtimePriceResponse(
                SHOWTIME_ID, ScreenType.SCREEN_2D, NOW.plusSeconds(3600), new BigDecimal("90000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(
                        new SeatTypePriceResponse("STANDARD", "Thường", BigDecimal.ZERO, new BigDecimal("100000")),
                        new SeatTypePriceResponse("VIP", "VIP", new BigDecimal("20000"), new BigDecimal("120000")))));
        when(seatOccupancyReader.findOccupiedSeats(any(Long.class), any(Instant.class))).thenReturn(Map.of(
                2L, SeatAvailability.HELD,
                3L, SeatAvailability.SOLD));

        ShowtimeSeatMapResponse showtimeSeatMapResponse = showtimeSeatMapService.getSeatMap(SHOWTIME_ID);

        List<ShowtimeSeatResponse> seats = showtimeSeatMapResponse.seats();
        assertEquals(SeatAvailability.AVAILABLE, seats.get(0).state());
        assertEquals(SeatAvailability.HELD, seats.get(1).state());
        assertEquals(SeatAvailability.SOLD, seats.get(2).state());
        assertEquals(SeatAvailability.AVAILABLE, seats.get(3).state());
        assertEquals(SeatAvailability.UNAVAILABLE, seats.get(4).state());
        assertEquals(new BigDecimal("100000"), seats.get(0).price());
        assertEquals(new BigDecimal("120000"), seats.get(3).price());
        assertEquals("Rạp A", showtimeSeatMapResponse.cinemaName());
        assertEquals("Phim", showtimeSeatMapResponse.movieTitle());
        assertEquals(NOW, showtimeSeatMapResponse.serverTime());
    }

    private ShowtimeInfoResponse showtimeInfo(ShowtimeStatus showtimeStatus, Instant startTime) {
        return new ShowtimeInfoResponse(
                SHOWTIME_ID, 7L, HALL_ID, startTime, startTime.plusSeconds(7200), new BigDecimal("90000"), showtimeStatus);
    }

    private SeatLayoutSeatResponse layoutSeat(Long id, int number, String type, SeatStatus seatStatus) {
        return new SeatLayoutSeatResponse(id, "A", number, type, BigDecimal.ZERO, 1, seatStatus);
    }
}
