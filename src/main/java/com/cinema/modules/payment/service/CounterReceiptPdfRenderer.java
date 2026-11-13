package com.cinema.modules.payment.service;

import com.cinema.modules.booking.dto.response.BookingDetailResponse;
import com.cinema.modules.booking.dto.response.BookingFnbLineResponse;
import com.cinema.modules.booking.dto.response.BookingShowtimeResponse;
import com.cinema.modules.booking.dto.response.BookingTicketResponse;
import com.cinema.modules.payment.dto.response.CounterFnbReceiptLineResponse;
import com.cinema.modules.payment.dto.response.CounterFnbReceiptResponse;
import com.cinema.modules.payment.dto.response.CounterReceiptResponse;
import com.cinema.modules.payment.enums.PaymentMethod;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.DottedLineSeparator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

@Component
public class CounterReceiptPdfRenderer {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DATA_URL_BASE64_MARKER = "base64,";
    private static final Color INK_900 = new Color(0x11, 0x18, 0x27);
    private static final Color INK_500 = new Color(0x6B, 0x72, 0x80);
    private static final Color DASH_COLOR = new Color(0xCB, 0xD5, 0xE1);
    private static final Map<PaymentMethod, String> PAYMENT_METHOD_LABELS = Map.of(
            PaymentMethod.CASH, "Tiền mặt",
            PaymentMethod.CARD, "Thẻ",
            PaymentMethod.TRANSFER, "Chuyển khoản",
            PaymentMethod.VNPAY, "VNPay");

    private final BaseFont regularBaseFont;
    private final BaseFont boldBaseFont;

    public CounterReceiptPdfRenderer() {
        this.regularBaseFont = loadBaseFont("fonts/NotoSans-Regular.ttf");
        this.boldBaseFont = loadBaseFont("fonts/NotoSans-Bold.ttf");
    }

    public byte[] render(CounterReceiptResponse counterReceiptResponse) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A5, 28, 28, 28, 28);
        try {
            PdfWriter.getInstance(document, byteArrayOutputStream);
            document.open();
            addHeader(document, counterReceiptResponse.booking().showtime().cinemaName(), "Vé và hóa đơn bán tại quầy");
            addShowtime(document, counterReceiptResponse.booking().showtime());
            addSeats(document, counterReceiptResponse.booking());
            addFnbLines(document, counterReceiptResponse.booking());
            addTotals(document, counterReceiptResponse);
            addQrCode(document, counterReceiptResponse.booking());
            addFooter(document, counterReceiptResponse);
        } catch (DocumentException | IOException exception) {
            throw new IllegalStateException("Không thể tạo tệp PDF hóa đơn", exception);
        } finally {
            document.close();
        }
        return byteArrayOutputStream.toByteArray();
    }

    public byte[] renderFnbReceipt(CounterFnbReceiptResponse counterFnbReceiptResponse) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A5, 28, 28, 28, 28);
        try {
            PdfWriter.getInstance(document, byteArrayOutputStream);
            document.open();
            addHeader(document, counterFnbReceiptResponse.cinemaName(), "Hóa đơn bắp nước bán tại quầy");
            addFnbOrderLines(document, counterFnbReceiptResponse);
            addFnbTotals(document, counterFnbReceiptResponse);
            addFnbOrderCode(document, counterFnbReceiptResponse);
            addFnbFooter(document, counterFnbReceiptResponse);
        } catch (DocumentException exception) {
            throw new IllegalStateException("Không thể tạo tệp PDF hóa đơn", exception);
        } finally {
            document.close();
        }
        return byteArrayOutputStream.toByteArray();
    }

    private void addHeader(Document document, String cinemaName, String subtitle) throws DocumentException {
        document.add(centeredParagraph("CinemaSphere", boldFont(16, INK_900), 2));
        document.add(centeredParagraph(cinemaName, regularFont(10, INK_900), 2));
        document.add(centeredParagraph(subtitle, regularFont(8, INK_500), 8));
    }

    private void addFnbOrderLines(Document document, CounterFnbReceiptResponse counterFnbReceiptResponse)
            throws DocumentException {
        document.add(divider());
        document.add(sectionTitle("BẮP NƯỚC"));
        PdfPTable pdfPTable = twoColumnTable();
        for (CounterFnbReceiptLineResponse counterFnbReceiptLineResponse : counterFnbReceiptResponse.lines()) {
            addRow(pdfPTable,
                    counterFnbReceiptLineResponse.quantity() + " × " + counterFnbReceiptLineResponse.name(),
                    formatVnd(counterFnbReceiptLineResponse.lineAmount()),
                    regularFont(10, INK_900), regularFont(10, INK_900));
        }
        document.add(pdfPTable);
    }

    private void addFnbTotals(Document document, CounterFnbReceiptResponse counterFnbReceiptResponse)
            throws DocumentException {
        document.add(divider());
        PdfPTable pdfPTable = twoColumnTable();
        addRow(pdfPTable, "Tổng thanh toán", formatVnd(counterFnbReceiptResponse.totalAmount()),
                regularFont(10, INK_500), boldFont(14, INK_900));
        addRow(pdfPTable, "Hình thức",
                PAYMENT_METHOD_LABELS.getOrDefault(
                        counterFnbReceiptResponse.paymentMethod(), counterFnbReceiptResponse.paymentMethod().name()),
                regularFont(10, INK_500), boldFont(10, INK_900));
        if (counterFnbReceiptResponse.cashReceived() != null) {
            addRow(pdfPTable, "Tiền khách đưa", formatVnd(counterFnbReceiptResponse.cashReceived()),
                    regularFont(10, INK_500), boldFont(10, INK_900));
        }
        if (counterFnbReceiptResponse.changeAmount() != null) {
            addRow(pdfPTable, "Tiền thối", formatVnd(counterFnbReceiptResponse.changeAmount()),
                    regularFont(10, INK_500), boldFont(10, INK_900));
        }
        document.add(pdfPTable);
    }

    private void addFnbOrderCode(Document document, CounterFnbReceiptResponse counterFnbReceiptResponse)
            throws DocumentException {
        document.add(divider());
        document.add(centeredParagraph(counterFnbReceiptResponse.orderCode(), boldFont(11, INK_900), 2));
        document.add(centeredParagraph("Khách nhận bắp nước ngay tại quầy.", regularFont(8, INK_500), 6));
    }

    private void addFnbFooter(Document document, CounterFnbReceiptResponse counterFnbReceiptResponse)
            throws DocumentException {
        Instant paidAt = counterFnbReceiptResponse.paidAt();
        document.add(divider());
        document.add(centeredParagraph(
                TIME_FORMATTER.format(paidAt.atZone(VIETNAM_ZONE)) + " · "
                        + DATE_FORMATTER.format(paidAt.atZone(VIETNAM_ZONE)),
                regularFont(8, INK_500), 1));
        if (counterFnbReceiptResponse.soldByStaffName() != null) {
            document.add(centeredParagraph(
                    "Nhân viên bán: " + counterFnbReceiptResponse.soldByStaffName(), regularFont(8, INK_500), 1));
        }
    }

    private void addShowtime(Document document, BookingShowtimeResponse bookingShowtimeResponse)
            throws DocumentException {
        document.add(divider());
        document.add(new Paragraph(bookingShowtimeResponse.movieTitle().toUpperCase(Locale.ROOT), boldFont(13, INK_900)));
        document.add(new Paragraph(
                bookingShowtimeResponse.hallName() + " · " + bookingShowtimeResponse.screenType().getCode(),
                regularFont(10, INK_900)));
        document.add(new Paragraph(
                DATE_FORMATTER.format(bookingShowtimeResponse.startTime().atZone(VIETNAM_ZONE))
                        + " · "
                        + TIME_FORMATTER.format(bookingShowtimeResponse.startTime().atZone(VIETNAM_ZONE))
                        + " – "
                        + TIME_FORMATTER.format(bookingShowtimeResponse.endTime().atZone(VIETNAM_ZONE)),
                boldFont(10, INK_900)));
    }

    private void addSeats(Document document, BookingDetailResponse bookingDetailResponse) throws DocumentException {
        document.add(divider());
        document.add(sectionTitle("GHẾ"));
        PdfPTable pdfPTable = twoColumnTable();
        for (BookingTicketResponse bookingTicketResponse : bookingDetailResponse.tickets()) {
            addRow(pdfPTable, "Ghế " + bookingTicketResponse.seatLabel(), formatVnd(bookingTicketResponse.price()),
                    regularFont(10, INK_900), regularFont(10, INK_900));
        }
        document.add(pdfPTable);
    }

    private void addFnbLines(Document document, BookingDetailResponse bookingDetailResponse)
            throws DocumentException {
        if (bookingDetailResponse.fnbLines().isEmpty()) {
            return;
        }
        document.add(divider());
        document.add(sectionTitle("BẮP NƯỚC"));
        PdfPTable pdfPTable = twoColumnTable();
        for (BookingFnbLineResponse bookingFnbLineResponse : bookingDetailResponse.fnbLines()) {
            addRow(pdfPTable,
                    bookingFnbLineResponse.quantity() + " × " + bookingFnbLineResponse.name(),
                    formatVnd(bookingFnbLineResponse.lineAmount()),
                    regularFont(10, INK_900), regularFont(10, INK_900));
        }
        document.add(pdfPTable);
    }

    private void addTotals(Document document, CounterReceiptResponse counterReceiptResponse)
            throws DocumentException {
        BookingDetailResponse bookingDetailResponse = counterReceiptResponse.booking();
        document.add(divider());
        PdfPTable pdfPTable = twoColumnTable();
        addRow(pdfPTable, "Tiền vé", formatVnd(bookingDetailResponse.ticketAmount()),
                regularFont(10, INK_500), boldFont(10, INK_900));
        addRow(pdfPTable, "Bắp nước", formatVnd(bookingDetailResponse.fnbAmount()),
                regularFont(10, INK_500), boldFont(10, INK_900));
        if (bookingDetailResponse.discountAmount().signum() > 0) {
            addRow(pdfPTable, "Giảm giá", "-" + formatVnd(bookingDetailResponse.discountAmount()),
                    regularFont(10, INK_500), boldFont(10, INK_900));
        }
        addRow(pdfPTable, "Tổng thanh toán", formatVnd(bookingDetailResponse.finalAmount()),
                regularFont(10, INK_500), boldFont(14, INK_900));
        addRow(pdfPTable, "Hình thức",
                PAYMENT_METHOD_LABELS.getOrDefault(
                        counterReceiptResponse.paymentMethod(), counterReceiptResponse.paymentMethod().name()),
                regularFont(10, INK_500), boldFont(10, INK_900));
        if (counterReceiptResponse.cashReceived() != null) {
            addRow(pdfPTable, "Tiền khách đưa", formatVnd(counterReceiptResponse.cashReceived()),
                    regularFont(10, INK_500), boldFont(10, INK_900));
        }
        if (counterReceiptResponse.changeAmount() != null) {
            addRow(pdfPTable, "Tiền thối", formatVnd(counterReceiptResponse.changeAmount()),
                    regularFont(10, INK_500), boldFont(10, INK_900));
        }
        document.add(pdfPTable);
    }

    private void addQrCode(Document document, BookingDetailResponse bookingDetailResponse)
            throws DocumentException, IOException {
        document.add(divider());
        String qrCodeDataUrl = bookingDetailResponse.qrCodeDataUrl();
        if (qrCodeDataUrl != null && qrCodeDataUrl.contains(DATA_URL_BASE64_MARKER)) {
            String base64 = qrCodeDataUrl.substring(
                    qrCodeDataUrl.indexOf(DATA_URL_BASE64_MARKER) + DATA_URL_BASE64_MARKER.length());
            Image image = Image.getInstance(Base64.getDecoder().decode(base64));
            image.scaleToFit(120, 120);
            image.setAlignment(Element.ALIGN_CENTER);
            document.add(image);
        }
        document.add(centeredParagraph(bookingDetailResponse.bookingCode(), boldFont(11, INK_900), 2));
        document.add(centeredParagraph(
                "Một mã QR cho cả đơn. Đưa mã này cho nhân viên để soát vé và nhận bắp nước.",
                regularFont(8, INK_500), 6));
    }

    private void addFooter(Document document, CounterReceiptResponse counterReceiptResponse)
            throws DocumentException {
        Instant paidAt = counterReceiptResponse.paidAt();
        document.add(divider());
        document.add(centeredParagraph(
                TIME_FORMATTER.format(paidAt.atZone(VIETNAM_ZONE)) + " · "
                        + DATE_FORMATTER.format(paidAt.atZone(VIETNAM_ZONE)),
                regularFont(8, INK_500), 1));
        if (counterReceiptResponse.soldByStaffName() != null) {
            document.add(centeredParagraph(
                    "Nhân viên bán: " + counterReceiptResponse.soldByStaffName(), regularFont(8, INK_500), 1));
        }
        String customerName = counterReceiptResponse.customerName() == null
                ? "Khách vãng lai"
                : counterReceiptResponse.customerName();
        document.add(centeredParagraph("Khách hàng: " + customerName, regularFont(8, INK_500), 1));
    }

    private Paragraph centeredParagraph(String text, Font font, float spacingAfter) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingAfter(spacingAfter);
        return paragraph;
    }

    private Paragraph sectionTitle(String text) {
        Paragraph paragraph = new Paragraph(text, boldFont(8, INK_500));
        paragraph.setSpacingAfter(4);
        return paragraph;
    }

    private Paragraph divider() {
        DottedLineSeparator dottedLineSeparator = new DottedLineSeparator();
        dottedLineSeparator.setLineColor(DASH_COLOR);
        dottedLineSeparator.setGap(3);
        Paragraph paragraph = new Paragraph(new Chunk(dottedLineSeparator));
        paragraph.setSpacingBefore(4);
        paragraph.setSpacingAfter(6);
        return paragraph;
    }

    private PdfPTable twoColumnTable() throws DocumentException {
        PdfPTable pdfPTable = new PdfPTable(new float[] {3f, 1.4f});
        pdfPTable.setWidthPercentage(100);
        pdfPTable.setSpacingAfter(4);
        return pdfPTable;
    }

    private void addRow(PdfPTable pdfPTable, String label, String value, Font labelFont, Font valueFont) {
        pdfPTable.addCell(borderlessCell(label, labelFont, Element.ALIGN_LEFT));
        pdfPTable.addCell(borderlessCell(value, valueFont, Element.ALIGN_RIGHT));
    }

    private PdfPCell borderlessCell(String text, Font font, int alignment) {
        PdfPCell pdfPCell = new PdfPCell(new Paragraph(text, font));
        pdfPCell.setBorder(Rectangle.NO_BORDER);
        pdfPCell.setHorizontalAlignment(alignment);
        pdfPCell.setPaddingTop(2);
        pdfPCell.setPaddingBottom(2);
        pdfPCell.setPaddingLeft(0);
        pdfPCell.setPaddingRight(0);
        return pdfPCell;
    }

    private Font regularFont(float size, Color color) {
        return new Font(regularBaseFont, size, Font.NORMAL, color);
    }

    private Font boldFont(float size, Color color) {
        return new Font(boldBaseFont, size, Font.NORMAL, color);
    }

    private String formatVnd(BigDecimal amount) {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.of("vi", "VN")));
        return decimalFormat.format(amount) + "đ";
    }

    private BaseFont loadBaseFont(String classpathLocation) {
        try (InputStream inputStream = new ClassPathResource(classpathLocation).getInputStream()) {
            return BaseFont.createFont(
                    classpathLocation, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, inputStream.readAllBytes(), null);
        } catch (IOException | DocumentException exception) {
            throw new IllegalStateException("Không nạp được phông chữ " + classpathLocation, exception);
        }
    }
}
