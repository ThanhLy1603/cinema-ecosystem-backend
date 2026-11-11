package com.cinema.modules.booking.service;

import com.cinema.config.properties.AppMailProperties;
import com.cinema.modules.auth.dto.response.UserContactResponse;
import com.cinema.modules.booking.dto.response.BookingDetailResponse;
import com.cinema.modules.booking.dto.response.BookingFnbLineResponse;
import com.cinema.modules.booking.dto.response.BookingSeatResponse;
import com.cinema.modules.booking.dto.response.BookingShowtimeResponse;
import com.cinema.modules.booking.dto.response.BookingTicketResponse;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.event.BookingPaidEvent;
import com.cinema.modules.booking.service.interfaces.IBookingDetailService;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.auth.service.interfaces.IUserQueryService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketMailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private IBookingDetailService bookingDetailService;

    @Mock
    private IUserQueryService userQueryService;

    private TicketMailService ticketMailService;

    @BeforeEach
    void setUp() {
        ticketMailService = new TicketMailService(
                javaMailSender,
                new AppMailProperties("CinemaSphere <noreply@example.com>", "http://localhost:3000/"),
                bookingDetailService,
                userQueryService,
                new QrCodeGenerator());
    }

    private BookingDetailResponse bookingDetail(String movieTitle) {
        return new BookingDetailResponse(
                100L, "BK123", BookingStatus.PAID, Instant.parse("2026-11-11T03:00:00Z"), null, Instant.now(),
                new BookingShowtimeResponse(20L, Instant.parse("2026-11-11T13:10:00Z"), Instant.parse("2026-11-11T15:10:00Z"),
                        7L, "phim", movieTitle, null, AgeRating.P, 120, 1L, "Star Cineplex", 3L, "Phòng 1", ScreenType.SCREEN_2D),
                List.of(new BookingSeatResponse(11L, "A7", "Thường", new BigDecimal("90000"))),
                List.of(new BookingFnbLineResponse(null, 7L, "Bắp ngọt", null, 2, new BigDecimal("35000"), new BigDecimal("70000"))),
                new BigDecimal("90000"), new BigDecimal("70000"), BigDecimal.ZERO, new BigDecimal("160000"),
                new BigDecimal("160000"),
                List.of(new BookingTicketResponse(1L, "TKABC234", 11L, "A7", new BigDecimal("90000"), false, null)),
                new QrCodeGenerator().toPngDataUrl("QRTESTCODE", 100));
    }

    @Test
    void invoiceHtmlListsShowtimeSeatsFnbTotalAndTicketCodeWithTheQrReference() {
        String html = ticketMailService.buildHtml(
                bookingDetail("Đứa con của thời tiết"), new UserContactResponse(5L, "a@b.c", "Lý"));

        assertTrue(html.contains("BK123"));
        assertTrue(html.contains("Đứa con của thời tiết"));
        assertTrue(html.contains("20:10 ngày 11/11/2026"));
        assertTrue(html.contains("gồm 1 ghế (A7) và bắp nước"));
        assertFalse(html.contains("cid:qr0"));
        assertTrue(html.contains("2 × Bắp ngọt"));
        assertTrue(html.contains("160.000đ"));
        assertTrue(html.contains("src=\"cid:qr\""));
        assertTrue(html.contains("http://localhost:3000/tickets"));
    }

    @Test
    void userSuppliedTextIsHtmlEscaped() {
        String html = ticketMailService.buildHtml(
                bookingDetail("<script>alert(1)</script>"), new UserContactResponse(5L, "a@b.c", "<b>Lý</b>"));

        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertFalse(html.contains("<b>Lý</b>"));
    }

    @Test
    void paidEventSendsOneMultipartMessageToTheCustomer() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(bookingDetailService.findBookingDetail(100L)).thenReturn(Optional.of(bookingDetail("Phim")));
        when(userQueryService.findContact(5L)).thenReturn(Optional.of(new UserContactResponse(5L, "a@b.c", "Lý")));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        ticketMailService.onBookingPaid(new BookingPaidEvent(100L, 5L, "BK123", new BigDecimal("160000")));

        verify(javaMailSender).send(mimeMessage);
        assertEquals("a@b.c", mimeMessage.getAllRecipients()[0].toString());
        assertTrue(mimeMessage.getSubject().contains("BK123"));
    }

    @Test
    void missingBookingOrRecipientSendsNothing() {
        when(bookingDetailService.findBookingDetail(100L)).thenReturn(Optional.empty());
        when(userQueryService.findContact(5L)).thenReturn(Optional.empty());

        ticketMailService.onBookingPaid(new BookingPaidEvent(100L, 5L, "BK123", new BigDecimal("160000")));

        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }
}
