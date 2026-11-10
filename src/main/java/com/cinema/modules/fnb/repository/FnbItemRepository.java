package com.cinema.modules.fnb.repository;

import com.cinema.modules.fnb.entity.FnbItem;
import com.cinema.modules.fnb.enums.FnbStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface FnbItemRepository extends JpaRepository<FnbItem, Long>, JpaSpecificationExecutor<FnbItem> {

    List<FnbItem> findAllByIdIn(Collection<Long> itemIds);

    List<FnbItem> findAllByDeletedFalseOrderByCategoryAscNameAscIdAsc();

    List<FnbItem> findAllByDeletedFalseAndStatusOrderByCategoryAscNameAscIdAsc(FnbStatus status);

    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndDeletedFalseAndIdNot(String name, Long itemId);
}
