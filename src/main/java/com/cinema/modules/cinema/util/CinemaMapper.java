package com.cinema.modules.cinema.util;

import com.cinema.modules.cinema.dto.response.CinemaDetailResponse;
import com.cinema.modules.cinema.dto.response.CinemaResponse;
import com.cinema.modules.cinema.dto.response.FacilityResponse;
import com.cinema.modules.cinema.dto.response.HallResponse;
import com.cinema.modules.cinema.dto.response.SeatLayoutResponse;
import com.cinema.modules.cinema.dto.response.SeatLayoutSeatResponse;
import com.cinema.modules.cinema.dto.response.SeatTypeResponse;
import com.cinema.modules.cinema.entity.Cinema;
import com.cinema.modules.cinema.entity.CinemaFacility;
import com.cinema.modules.cinema.entity.Hall;
import com.cinema.modules.cinema.entity.Seat;
import com.cinema.modules.cinema.entity.SeatType;

import java.util.Comparator;
import java.util.List;

public final class CinemaMapper {

    private CinemaMapper() {
    }

    public static FacilityResponse toFacilityResponse(CinemaFacility cinemaFacility) {
        return new FacilityResponse(
                cinemaFacility.getId(),
                cinemaFacility.getName(),
                cinemaFacility.getIconUrl(),
                cinemaFacility.getDescription(),
                cinemaFacility.isDeleted());
    }

    public static List<FacilityResponse> toFacilityResponses(Cinema cinema) {
        return cinema.getFacilities().stream()
                .filter(cinemaFacility -> !cinemaFacility.isDeleted())
                .sorted(Comparator.comparing(CinemaFacility::getName, String.CASE_INSENSITIVE_ORDER))
                .map(CinemaMapper::toFacilityResponse)
                .toList();
    }

    public static HallResponse toHallResponse(Hall hall) {
        return new HallResponse(
                hall.getId(),
                hall.getCinemaId(),
                hall.getName(),
                hall.getScreenType(),
                hall.getTotalSeats(),
                hall.getStatus(),
                hall.isDeleted());
    }

    public static CinemaResponse toCinemaResponse(Cinema cinema, int hallCount) {
        return new CinemaResponse(
                cinema.getId(),
                cinema.getName(),
                cinema.getSlug(),
                cinema.getCity(),
                cinema.getAddress(),
                cinema.getPhone(),
                cinema.getLatitude(),
                cinema.getLongitude(),
                cinema.getStatus(),
                toFacilityResponses(cinema),
                hallCount,
                cinema.isDeleted());
    }

    public static CinemaDetailResponse toCinemaDetailResponse(Cinema cinema, List<Hall> halls) {
        return new CinemaDetailResponse(
                cinema.getId(),
                cinema.getName(),
                cinema.getSlug(),
                cinema.getCity(),
                cinema.getAddress(),
                cinema.getPhone(),
                cinema.getLatitude(),
                cinema.getLongitude(),
                cinema.getStatus(),
                toFacilityResponses(cinema),
                halls.stream().map(CinemaMapper::toHallResponse).toList(),
                cinema.isDeleted());
    }

    public static SeatTypeResponse toSeatTypeResponse(SeatType seatType) {
        return new SeatTypeResponse(
                seatType.getId(),
                seatType.getCode(),
                seatType.getName(),
                seatType.getDefaultSurcharge(),
                seatType.getSeatSpan());
    }

    public static SeatLayoutResponse toSeatLayoutResponse(Hall hall, List<Seat> seats) {
        List<Seat> sortedSeats = seats.stream()
                .sorted(Comparator.comparing(Seat::getSeatRow).thenComparingInt(Seat::getSeatNumber))
                .toList();
        List<SeatLayoutSeatResponse> seatLayoutSeatResponses = sortedSeats.stream()
                .map(seat -> new SeatLayoutSeatResponse(
                        seat.getId(),
                        seat.getSeatRow(),
                        seat.getSeatNumber(),
                        seat.getSeatType().getCode(),
                        seat.getSeatType().getDefaultSurcharge(),
                        seat.getSeatType().getSeatSpan(),
                        seat.getStatus()))
                .toList();
        List<String> rows = sortedSeats.stream().map(Seat::getSeatRow).distinct().toList();
        int columns = sortedSeats.stream()
                .mapToInt(seat -> seat.getSeatNumber() + seat.getSeatType().getSeatSpan() - 1)
                .max()
                .orElse(0);
        return new SeatLayoutResponse(
                hall.getId(),
                hall.getCinemaId(),
                hall.getName(),
                hall.getScreenType(),
                hall.getStatus(),
                hall.getVersion(),
                rows,
                columns,
                seatLayoutSeatResponses);
    }
}
