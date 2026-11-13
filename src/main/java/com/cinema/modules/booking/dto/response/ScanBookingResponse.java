package com.cinema.modules.booking.dto.response;

import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.payment.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ScanBookingResponse(
        Long bookingId,
        String bookingCode,
        BookingStatus bookingStatus,
        Instant bookedAt,
        PaymentMethod paymentMethod,
        String movieTitle,
        String posterUrl,
        AgeRating rated,
        String cinemaName,
        String hallName,
        ScreenType screenType,
        Instant startTime,
        Instant endTime,
        List<ScanTicketResponse> tickets,
        List<ScanFnbLineResponse> fnbLines,
        BigDecimal finalAmount) {
}
