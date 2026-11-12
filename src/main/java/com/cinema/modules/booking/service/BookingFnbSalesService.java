package com.cinema.modules.booking.service;

import com.cinema.modules.booking.dto.response.FnbBestSellerResponse;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.repository.BookingFnbDetailRepository;
import com.cinema.modules.booking.service.interfaces.IBookingFnbSalesService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BookingFnbSalesService implements IBookingFnbSalesService {

    private static final int BEST_SELLER_COMBO_LIMIT = 1;
    private static final int BEST_SELLER_ITEM_LIMIT = 3;

    private final BookingFnbDetailRepository bookingFnbDetailRepository;

    public BookingFnbSalesService(BookingFnbDetailRepository bookingFnbDetailRepository) {
        this.bookingFnbDetailRepository = bookingFnbDetailRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public FnbBestSellerResponse findBestSellers() {
        List<Long> comboIds = bookingFnbDetailRepository.findComboIdsBySoldQuantityDesc(
                BookingStatus.PAID, PageRequest.of(0, BEST_SELLER_COMBO_LIMIT));
        List<Long> itemIds = bookingFnbDetailRepository.findItemIdsBySoldQuantityDesc(
                BookingStatus.PAID, PageRequest.of(0, BEST_SELLER_ITEM_LIMIT));
        return new FnbBestSellerResponse(comboIds, itemIds);
    }
}
