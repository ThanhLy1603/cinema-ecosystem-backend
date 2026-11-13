package com.cinema.modules.staff.repository;

import com.cinema.modules.staff.entity.StaffShift;
import com.cinema.modules.staff.enums.ShiftStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffShiftRepository extends JpaRepository<StaffShift, Long>, JpaSpecificationExecutor<StaffShift> {

    Optional<StaffShift> findFirstByStaffUserIdAndStatus(Long staffUserId, ShiftStatus shiftStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select staffShift from StaffShift staffShift "
            + "where staffShift.staffUserId = :staffUserId and staffShift.status = :shiftStatus")
    Optional<StaffShift> findByStaffUserIdAndStatusForUpdate(
            @Param("staffUserId") Long staffUserId, @Param("shiftStatus") ShiftStatus shiftStatus);
}
