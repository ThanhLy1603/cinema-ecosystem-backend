package com.cinema.modules.fnb.repository;

import com.cinema.modules.fnb.entity.FnbCombo;
import com.cinema.modules.fnb.enums.FnbStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FnbComboRepository extends JpaRepository<FnbCombo, Long>, JpaSpecificationExecutor<FnbCombo> {

    @EntityGraph(attributePaths = {"fnbComboItems", "fnbComboItems.fnbItem"})
    List<FnbCombo> findWithItemsByIdIn(Collection<Long> comboIds);

    @EntityGraph(attributePaths = {"fnbComboItems", "fnbComboItems.fnbItem"})
    Optional<FnbCombo> findWithItemsById(Long comboId);

    @EntityGraph(attributePaths = {"fnbComboItems", "fnbComboItems.fnbItem"})
    List<FnbCombo> findAllByDeletedFalseAndStatusOrderByNameAscIdAsc(FnbStatus status);

    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndDeletedFalseAndIdNot(String name, Long comboId);

    @Query("select count(fnbCombo) from FnbCombo fnbCombo join fnbCombo.fnbComboItems fnbComboItem "
            + "where fnbComboItem.fnbItem.id = :itemId and fnbCombo.deleted = false")
    long countActiveCombosUsingItem(@Param("itemId") Long itemId);
}
