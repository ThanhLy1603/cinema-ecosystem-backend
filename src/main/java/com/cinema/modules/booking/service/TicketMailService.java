package com.cinema.modules.booking.service;

import com.cinema.common.util.BusinessZone;
import com.cinema.config.properties.AppMailProperties;
import com.cinema.modules.auth.dto.response.UserContactResponse;
import com.cinema.modules.auth.service.interfaces.IUserQueryService;
import com.cinema.modules.booking.dto.response.BookingDetailResponse;
import com.cinema.modules.booking.dto.response.BookingFnbLineResponse;
import com.cinema.modules.booking.dto.response.BookingTicketResponse;
import com.cinema.modules.booking.event.BookingPaidEvent;
import com.cinema.modules.booking.service.interfaces.IBookingDetailService;
import com.cinema.modules.booking.service.interfaces.ITicketMailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Base64;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class TicketMailService implements ITicketMailService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy").withZone(BusinessZone.VIETNAM);
    private static final String TICKETS_PATH = "/tickets";
    private static final String QR_CONTENT_ID = "qr";

    private final JavaMailSender javaMailSender;
    private final AppMailProperties appMailProperties;
    private final IBookingDetailService bookingDetailService;
    private final IUserQueryService userQueryService;
    private final QrCodeGenerator qrCodeGenerator;

    public TicketMailService(
            JavaMailSender javaMailSender,
            AppMailProperties appMailProperties,
            IBookingDetailService bookingDetailService,
            IUserQueryService userQueryService,
            QrCodeGenerator qrCodeGenerator) {
        this.javaMailSender = javaMailSender;
        this.appMailProperties = appMailProperties;
        this.bookingDetailService = bookingDetailService;
        this.userQueryService = userQueryService;
        this.qrCodeGenerator = qrCodeGenerator;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Override
    public void onBookingPaid(BookingPaidEvent bookingPaidEvent) {
        Optional<BookingDetailResponse> bookingDetailResponse =
                bookingDetailService.findBookingDetail(bookingPaidEvent.bookingId());
        Optional<UserContactResponse> userContactResponse = userQueryService.findContact(bookingPaidEvent.userId());
        if (bookingDetailResponse.isEmpty() || userContactResponse.isEmpty()) {
            log.warn("Không gửi được email vé cho đơn {}: thiếu dữ liệu đơn hoặc người nhận", bookingPaidEvent.bookingCode());
            return;
        }
        try {
            javaMailSender.send(buildMessage(bookingDetailResponse.get(), userContactResponse.get()));
        } catch (MailException | MessagingException exception) {
            log.warn("Không gửi được email vé cho đơn {}", bookingPaidEvent.bookingCode(), exception);
        }
    }

    private MimeMessage buildMessage(BookingDetailResponse bookingDetailResponse, UserContactResponse userContactResponse)
            throws MessagingException {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper mimeMessageHelper =
                new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
        mimeMessageHelper.setFrom(appMailProperties.from());
        mimeMessageHelper.setTo(userContactResponse.email());
        mimeMessageHelper.setSubject("[CinemaSphere] Vé điện tử " + bookingDetailResponse.showtime().movieTitle()
                + " - " + bookingDetailResponse.bookingCode());
        mimeMessageHelper.setText(buildHtml(bookingDetailResponse, userContactResponse), true);
        if (bookingDetailResponse.qrCodeDataUrl() != null) {
            byte[] qrBytes = Base64.getDecoder().decode(
                    bookingDetailResponse.qrCodeDataUrl().substring(bookingDetailResponse.qrCodeDataUrl().indexOf(',') + 1));
            mimeMessageHelper.addInline(QR_CONTENT_ID, new ByteArrayResource(qrBytes), "image/png");
        }
        return mimeMessage;
    }

    String buildHtml(BookingDetailResponse bookingDetailResponse, UserContactResponse userContactResponse) {
        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;color:#1f2937\">");
        html.append("<h2 style=\"color:#c2410c\">Đặt vé thành công</h2>");
        html.append("<p>Xin chào ").append(escape(userContactResponse.fullName())).append(",</p>");
        html.append("<p>Cảm ơn bạn đã đặt vé tại CinemaSphere. Dưới đây là thông tin hóa đơn và vé điện tử của bạn.</p>");
        html.append("<table style=\"width:100%;border-collapse:collapse\">");
        appendRow(html, "Mã đơn", bookingDetailResponse.bookingCode());
        appendRow(html, "Phim", bookingDetailResponse.showtime().movieTitle());
        appendRow(html, "Rạp", bookingDetailResponse.showtime().cinemaName() + " · "
                + bookingDetailResponse.showtime().hallName() + " · " + bookingDetailResponse.showtime().screenType());
        appendRow(html, "Suất chiếu", DATE_TIME_FORMATTER.format(bookingDetailResponse.showtime().startTime()));
        appendRow(html, "Tiền vé", formatVnd(bookingDetailResponse.ticketAmount()));
        for (BookingFnbLineResponse bookingFnbLineResponse : bookingDetailResponse.fnbLines()) {
            appendRow(html, bookingFnbLineResponse.quantity() + " × " + bookingFnbLineResponse.name(),
                    formatVnd(bookingFnbLineResponse.lineAmount()));
        }
        if (bookingDetailResponse.discountAmount().signum() > 0) {
            appendRow(html, "Giảm giá", "-" + formatVnd(bookingDetailResponse.discountAmount()));
        }
        appendRow(html, "Tổng thanh toán", formatVnd(bookingDetailResponse.finalAmount()));
        html.append("</table>");
        html.append("<h3>Mã QR vào rạp</h3>");
        html.append("<div style=\"text-align:center;border:1px solid #e5e7eb;border-radius:12px;padding:16px;margin-bottom:12px\">");
        if (bookingDetailResponse.qrCodeDataUrl() != null) {
            html.append("<img src=\"cid:").append(QR_CONTENT_ID).append("\" width=\"180\" height=\"180\" alt=\"QR\"/>");
        }
        html.append("<div style=\"font-size:18px;font-weight:bold;margin-top:8px\">")
                .append(escape(bookingDetailResponse.bookingCode())).append("</div>");
        html.append("<div style=\"color:#6b7280\">Một mã QR duy nhất cho cả đơn, gồm ")
                .append(bookingDetailResponse.tickets().size()).append(" ghế (")
                .append(escape(bookingDetailResponse.tickets().stream()
                        .map(BookingTicketResponse::seatLabel)
                        .collect(java.util.stream.Collectors.joining(", "))))
                .append(")");
        if (!bookingDetailResponse.fnbLines().isEmpty()) {
            html.append(" và bắp nước");
        }
        html.append("</div></div>");
        html.append("<p>Đưa mã QR này cho nhân viên rạp để soát vé cho tất cả các ghế trong đơn. Bạn cũng có thể xem lại vé tại <a href=\"")
                .append(escape(buildTicketsLink())).append("\">Vé của tôi</a>.</p>");
        html.append("<p style=\"color:#6b7280\">CinemaSphere</p></div>");
        return html.toString();
    }

    private void appendRow(StringBuilder html, String label, String value) {
        html.append("<tr><td style=\"padding:6px 0;color:#6b7280\">").append(escape(label))
                .append("</td><td style=\"padding:6px 0;text-align:right;font-weight:600\">")
                .append(escape(value)).append("</td></tr>");
    }

    private String buildTicketsLink() {
        String baseUrl = appMailProperties.frontendBaseUrl();
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + TICKETS_PATH;
    }

    private String formatVnd(BigDecimal amount) {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(amount) + "đ";
    }

    private String escape(String text) {
        return HtmlUtils.htmlEscape(text == null ? "" : text, StandardCharsets.UTF_8.name());
    }
}
