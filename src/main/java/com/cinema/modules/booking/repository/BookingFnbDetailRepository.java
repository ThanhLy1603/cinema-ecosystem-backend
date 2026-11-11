package com.cinema.modules.booking.repository;

import com.cinema.modules.booking.entity.BookingFnbDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface BookingFnbDetailRepository extends JpaRepository<BookingFnbDetail, Long> {

    List<BookingFnbDetail> findByBookingIdOrderByIdAsc(Long bookingId);

    @Modifying
    @Query("delete from BookingFnbDetail bookingFnbDetail where bookingFnbDetail.bookingId = :bookingId")
    int deleteAllByBookingId(@Param("bookingId") Long bookingId);

    @Query("select sum(bookingFnbDetail.unitPrice * bookingFnbDetail.quantity) from BookingFnbDetail bookingFnbDetail "
            + "where bookingFnbDetail.bookingId = :bookingId")
    BigDecimal sumAmountByBookingId(@Param("bookingId") Long bookingId);
}
