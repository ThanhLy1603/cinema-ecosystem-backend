package com.cinema.modules.booking.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.entity.SeatReservation;
import com.cinema.modules.booking.repository.SeatReservationRepository;
import com.cinema.modules.booking.util.SeatGapRule;
import com.cinema.modules.cinema.dto.response.SeatLayoutResponse;
import com.cinema.modules.cinema.enums.SeatStatus;
import com.cinema.modules.cinema.service.interfaces.ICinemaPublicService;
import com.cinema.modules.showtime.dto.response.ShowtimeInfoResponse;
import com.cinema.modules.showtime.service.interfaces.IShowtimeQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class SeatGapChecker {

    private final SeatReservationRepository seatReservationRepository;
    private final SeatOccupancyReader seatOccupancyReader;
    private final IShowtimeQueryService showtimeQueryService;
    private final ICinemaPublicService cinemaPublicService;

    public SeatGapChecker(
            SeatReservationRepository seatReservationRepository,
            SeatOccupancyReader seatOccupancyReader,
            IShowtimeQueryService showtimeQueryService,
            ICinemaPublicService cinemaPublicService) {
        this.seatReservationRepository = seatReservationRepository;
        this.seatOccupancyReader = seatOccupancyReader;
        this.showtimeQueryService = showtimeQueryService;
        this.cinemaPublicService = cinemaPublicService;
    }

    public void validateNoOrphanSeats(Booking booking, Instant now) {
        ShowtimeInfoResponse showtimeInfoResponse = showtimeQueryService.findShowtimeInfo(booking.getShowtimeId())
                .orElseThrow(() -> new AppException("Không tìm thấy suất chiếu", HttpStatus.NOT_FOUND, "SHOWTIME_NOT_FOUND"));
        SeatLayoutResponse seatLayoutResponse = cinemaPublicService.findPublicSeatLayout(showtimeInfoResponse.hallId());
        Set<Long> selectedSeatIds = new HashSet<>();
        for (SeatReservation seatReservation : seatReservationRepository.findByBookingIdOrderBySeatId(booking.getId())) {
            selectedSeatIds.add(seatReservation.getSeatId());
        }
        Set<Long> blockedSeatIds = new HashSet<>(seatOccupancyReader.findOccupiedSeats(booking.getShowtimeId(), now).keySet());
        seatLayoutResponse.seats().stream()
                .filter(layoutSeat -> layoutSeat.status() == SeatStatus.MAINTENANCE)
                .forEach(layoutSeat -> blockedSeatIds.add(layoutSeat.id()));
        blockedSeatIds.removeAll(selectedSeatIds);
        List<String> orphanSeatLabels =
                SeatGapRule.findOrphanSeatLabels(seatLayoutResponse.seats(), selectedSeatIds, blockedSeatIds);
        if (!orphanSeatLabels.isEmpty()) {
            throw new AppException(
                    "Vui lòng không để trống 1 ghế bên cạnh hoặc giữa các ghế bạn chọn (ghế "
                            + String.join(", ", orphanSeatLabels) + ")",
                    HttpStatus.CONFLICT,
                    "SEAT_GAP_LEFT");
        }
    }
}
