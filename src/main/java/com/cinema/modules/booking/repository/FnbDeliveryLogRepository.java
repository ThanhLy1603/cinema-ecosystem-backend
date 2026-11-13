package com.cinema.modules.booking.repository;

import com.cinema.modules.booking.entity.FnbDeliveryLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FnbDeliveryLogRepository
        extends JpaRepository<FnbDeliveryLog, Long>, JpaSpecificationExecutor<FnbDeliveryLog> {

    List<FnbDeliveryLog> findByCinemaIdOrderByIdDesc(Long cinemaId, Pageable pageable);
}
