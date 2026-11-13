package com.cinema.modules.booking.repository;

import com.cinema.modules.booking.entity.BookingFnbDetail;
import com.cinema.modules.booking.enums.BookingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Repository
public interface BookingFnbDetailRepository extends JpaRepository<BookingFnbDetail, Long> {

    List<BookingFnbDetail> findByBookingIdOrderByIdAsc(Long bookingId);

    List<BookingFnbDetail> findByBookingIdInOrderByIdAsc(Collection<Long> bookingIds);

    @Modifying
    @Query("delete from BookingFnbDetail bookingFnbDetail where bookingFnbDetail.bookingId = :bookingId")
    int deleteAllByBookingId(@Param("bookingId") Long bookingId);

    @Query("select sum(bookingFnbDetail.unitPrice * bookingFnbDetail.quantity) from BookingFnbDetail bookingFnbDetail "
            + "where bookingFnbDetail.bookingId = :bookingId")
    BigDecimal sumAmountByBookingId(@Param("bookingId") Long bookingId);

    @Query("select bookingFnbDetail.comboId from BookingFnbDetail bookingFnbDetail "
            + "join Booking booking on booking.id = bookingFnbDetail.bookingId "
            + "where bookingFnbDetail.comboId is not null and booking.status = :status "
            + "group by bookingFnbDetail.comboId "
            + "order by sum(bookingFnbDetail.quantity) desc, bookingFnbDetail.comboId asc")
    List<Long> findComboIdsBySoldQuantityDesc(@Param("status") BookingStatus status, Pageable pageable);

    @Query("select bookingFnbDetail.itemId from BookingFnbDetail bookingFnbDetail "
            + "join Booking booking on booking.id = bookingFnbDetail.bookingId "
            + "where bookingFnbDetail.itemId is not null and booking.status = :status "
            + "group by bookingFnbDetail.itemId "
            + "order by sum(bookingFnbDetail.quantity) desc, bookingFnbDetail.itemId asc")
    List<Long> findItemIdsBySoldQuantityDesc(@Param("status") BookingStatus status, Pageable pageable);
}
