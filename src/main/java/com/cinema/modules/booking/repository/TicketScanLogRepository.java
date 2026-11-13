package com.cinema.modules.booking.repository;

import com.cinema.modules.booking.entity.TicketScanLog;
import com.cinema.modules.booking.enums.CheckInResultCode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TicketScanLogRepository
        extends JpaRepository<TicketScanLog, Long>, JpaSpecificationExecutor<TicketScanLog> {

    List<TicketScanLog> findByCinemaIdOrderByIdDesc(Long cinemaId, Pageable pageable);

    List<TicketScanLog> findByCinemaIdAndResultCodeInOrderByIdDesc(
            Long cinemaId, Collection<CheckInResultCode> resultCodes, Pageable pageable);
}
