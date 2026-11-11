package com.cinema.modules.booking.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.booking.dto.request.BookingFnbLineRequest;
import com.cinema.modules.booking.dto.request.BookingFnbRequest;
import com.cinema.modules.booking.dto.response.BookingFnbLineResponse;
import com.cinema.modules.booking.dto.response.BookingFnbResponse;
import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.entity.BookingFnbDetail;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.repository.BookingFnbDetailRepository;
import com.cinema.modules.booking.repository.BookingRepository;
import com.cinema.modules.booking.service.interfaces.IBookingFnbService;
import com.cinema.modules.booking.service.interfaces.ISeatHoldService;
import com.cinema.modules.fnb.dto.response.FnbProductInfoResponse;
import com.cinema.modules.fnb.service.interfaces.IFnbQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class BookingFnbService implements IBookingFnbService {

    private final BookingRepository bookingRepository;
    private final BookingFnbDetailRepository bookingFnbDetailRepository;
    private final IFnbQueryService fnbQueryService;
    private final ISeatHoldService seatHoldService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public BookingFnbService(
            BookingRepository bookingRepository,
            BookingFnbDetailRepository bookingFnbDetailRepository,
            IFnbQueryService fnbQueryService,
            ISeatHoldService seatHoldService,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.bookingFnbDetailRepository = bookingFnbDetailRepository;
        this.fnbQueryService = fnbQueryService;
        this.seatHoldService = seatHoldService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Override
    public BookingFnbResponse getPendingFnb(Long userId) {
        Instant now = Instant.now(clock);
        return transactionTemplate.execute(transactionStatus -> bookingRepository
                .findFirstByUserIdAndStatus(userId, BookingStatus.PENDING)
                .filter(booking -> booking.getExpiresAt().isAfter(now))
                .map(booking -> toBookingFnbResponse(
                        userId, booking, bookingFnbDetailRepository.findByBookingIdOrderByIdAsc(booking.getId()), now))
                .orElseGet(() -> emptyResponse(now)));
    }

    @Override
    public BookingFnbResponse replacePendingFnb(Long userId, BookingFnbRequest bookingFnbRequest) {
        Instant now = Instant.now(clock);
        return transactionTemplate.execute(
                transactionStatus -> replacePendingFnbInTransaction(userId, bookingFnbRequest, now));
    }

    private BookingFnbResponse replacePendingFnbInTransaction(
            Long userId, BookingFnbRequest bookingFnbRequest, Instant now) {
        Booking booking = bookingRepository.findByUserIdAndStatusForUpdate(userId, BookingStatus.PENDING)
                .filter(pendingBooking -> pendingBooking.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new AppException(
                        "Bạn chưa giữ ghế hoặc đã hết thời gian giữ ghế", HttpStatus.CONFLICT, "NO_PENDING_BOOKING"));
        List<BookingFnbDetail> bookingFnbDetails = buildDetails(booking.getId(), bookingFnbRequest.lines());
        bookingFnbDetailRepository.deleteAllByBookingId(booking.getId());
        bookingFnbDetailRepository.saveAll(bookingFnbDetails);
        bookingFnbDetailRepository.flush();
        BookingFnbResponse bookingFnbResponse = toBookingFnbResponse(userId, booking, bookingFnbDetails, now);
        BigDecimal discountAmount = Objects.requireNonNullElse(booking.getDiscountAmount(), BigDecimal.ZERO);
        booking.setTotalAmount(bookingFnbResponse.totalAmount());
        booking.setFinalAmount(bookingFnbResponse.totalAmount().subtract(discountAmount));
        bookingRepository.save(booking);
        return bookingFnbResponse;
    }

    private List<BookingFnbDetail> buildDetails(Long bookingId, List<BookingFnbLineRequest> bookingFnbLineRequests) {
        Set<String> lineKeys = new HashSet<>();
        for (BookingFnbLineRequest bookingFnbLineRequest : bookingFnbLineRequests) {
            if ((bookingFnbLineRequest.comboId() == null) == (bookingFnbLineRequest.itemId() == null)) {
                throw new AppException(
                        "Mỗi dòng phải chọn đúng một combo hoặc một món", HttpStatus.BAD_REQUEST, "FNB_LINE_INVALID");
            }
            if (!lineKeys.add(toLineKey(bookingFnbLineRequest))) {
                throw new AppException(
                        "Mỗi combo hoặc món chỉ được xuất hiện một lần", HttpStatus.BAD_REQUEST, "FNB_DUPLICATE_LINE");
            }
        }
        Map<Long, FnbProductInfoResponse> comboInfos = fnbQueryService.findComboInfos(
                bookingFnbLineRequests.stream().map(BookingFnbLineRequest::comboId).filter(Objects::nonNull).toList());
        Map<Long, FnbProductInfoResponse> itemInfos = fnbQueryService.findItemInfos(
                bookingFnbLineRequests.stream().map(BookingFnbLineRequest::itemId).filter(Objects::nonNull).toList());
        return bookingFnbLineRequests.stream()
                .map(bookingFnbLineRequest -> {
                    boolean isCombo = bookingFnbLineRequest.comboId() != null;
                    FnbProductInfoResponse fnbProductInfoResponse = isCombo
                            ? comboInfos.get(bookingFnbLineRequest.comboId())
                            : itemInfos.get(bookingFnbLineRequest.itemId());
                    return toBookingFnbDetail(bookingId, bookingFnbLineRequest, fnbProductInfoResponse);
                })
                .toList();
    }

    private BookingFnbDetail toBookingFnbDetail(
            Long bookingId,
            BookingFnbLineRequest bookingFnbLineRequest,
            FnbProductInfoResponse fnbProductInfoResponse) {
        if (fnbProductInfoResponse == null) {
            throw new AppException("Không tìm thấy combo hoặc món đã chọn", HttpStatus.NOT_FOUND, "FNB_PRODUCT_NOT_FOUND");
        }
        if (!fnbProductInfoResponse.orderable()) {
            throw new AppException(
                    "\"" + fnbProductInfoResponse.name() + "\" hiện không còn bán",
                    HttpStatus.CONFLICT,
                    "FNB_PRODUCT_UNAVAILABLE");
        }
        BookingFnbDetail bookingFnbDetail = new BookingFnbDetail();
        bookingFnbDetail.setBookingId(bookingId);
        bookingFnbDetail.setComboId(bookingFnbLineRequest.comboId());
        bookingFnbDetail.setItemId(bookingFnbLineRequest.itemId());
        bookingFnbDetail.setQuantity(bookingFnbLineRequest.quantity());
        bookingFnbDetail.setUnitPrice(fnbProductInfoResponse.price());
        return bookingFnbDetail;
    }

    private BookingFnbResponse toBookingFnbResponse(
            Long userId, Booking booking, List<BookingFnbDetail> bookingFnbDetails, Instant now) {
        Map<Long, FnbProductInfoResponse> comboInfos = fnbQueryService.findComboInfos(
                bookingFnbDetails.stream().map(BookingFnbDetail::getComboId).filter(Objects::nonNull).toList());
        Map<Long, FnbProductInfoResponse> itemInfos = fnbQueryService.findItemInfos(
                bookingFnbDetails.stream().map(BookingFnbDetail::getItemId).filter(Objects::nonNull).toList());
        List<BookingFnbLineResponse> bookingFnbLineResponses = bookingFnbDetails.stream()
                .map(bookingFnbDetail -> toBookingFnbLineResponse(bookingFnbDetail, comboInfos, itemInfos))
                .toList();
        BigDecimal fnbAmount = bookingFnbLineResponses.stream()
                .map(BookingFnbLineResponse::lineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ticketAmount = seatHoldService.getMyHold(userId, booking.getShowtimeId()).totalAmount();
        return new BookingFnbResponse(
                booking.getId(),
                booking.getExpiresAt(),
                now,
                bookingFnbLineResponses,
                ticketAmount,
                fnbAmount,
                ticketAmount.add(fnbAmount));
    }

    private BookingFnbLineResponse toBookingFnbLineResponse(
            BookingFnbDetail bookingFnbDetail,
            Map<Long, FnbProductInfoResponse> comboInfos,
            Map<Long, FnbProductInfoResponse> itemInfos) {
        FnbProductInfoResponse fnbProductInfoResponse = bookingFnbDetail.getComboId() != null
                ? comboInfos.get(bookingFnbDetail.getComboId())
                : itemInfos.get(bookingFnbDetail.getItemId());
        return new BookingFnbLineResponse(
                bookingFnbDetail.getComboId(),
                bookingFnbDetail.getItemId(),
                fnbProductInfoResponse == null ? "Món không còn trong thực đơn" : fnbProductInfoResponse.name(),
                fnbProductInfoResponse == null ? null : fnbProductInfoResponse.imageUrl(),
                bookingFnbDetail.getQuantity(),
                bookingFnbDetail.getUnitPrice(),
                bookingFnbDetail.getUnitPrice().multiply(BigDecimal.valueOf(bookingFnbDetail.getQuantity())));
    }

    private BookingFnbResponse emptyResponse(Instant now) {
        return new BookingFnbResponse(
                null, null, now, List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private String toLineKey(BookingFnbLineRequest bookingFnbLineRequest) {
        return bookingFnbLineRequest.comboId() != null
                ? "COMBO:" + bookingFnbLineRequest.comboId()
                : "ITEM:" + bookingFnbLineRequest.itemId();
    }
}
