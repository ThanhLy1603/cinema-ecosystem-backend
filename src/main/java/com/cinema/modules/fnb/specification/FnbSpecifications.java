package com.cinema.modules.fnb.specification;

import com.cinema.common.specification.SoftDeleteSpecifications;
import com.cinema.modules.fnb.entity.FnbCombo;
import com.cinema.modules.fnb.entity.FnbItem;
import com.cinema.modules.fnb.enums.FnbCategory;
import com.cinema.modules.fnb.enums.FnbStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class FnbSpecifications {

    private FnbSpecifications() {
    }

    public static Specification<FnbItem> searchItems(
            String keyword, FnbCategory category, FnbStatus status, boolean deleted) {
        Specification<FnbItem> specification = Specification.where(SoftDeleteSpecifications.<FnbItem>deletedIs(deleted));
        specification = specification.and(itemCategoryFilter(category));
        specification = specification.and(itemStatusFilter(status));
        specification = specification.and(itemNameFilter(keyword));
        return specification;
    }

    public static Specification<FnbCombo> searchCombos(String keyword, FnbStatus status, boolean deleted) {
        Specification<FnbCombo> specification =
                Specification.where(SoftDeleteSpecifications.<FnbCombo>deletedIs(deleted));
        specification = specification.and(comboStatusFilter(status));
        specification = specification.and(comboNameFilter(keyword));
        return specification;
    }

    static Specification<FnbItem> itemCategoryFilter(FnbCategory category) {
        if (category == null) {
            return null;
        }
        return (fnbItem, criteriaQuery, criteriaBuilder) -> criteriaBuilder.equal(fnbItem.get("category"), category);
    }

    static Specification<FnbItem> itemStatusFilter(FnbStatus status) {
        if (status == null) {
            return null;
        }
        return (fnbItem, criteriaQuery, criteriaBuilder) -> criteriaBuilder.equal(fnbItem.get("status"), status);
    }

    static Specification<FnbCombo> comboStatusFilter(FnbStatus status) {
        if (status == null) {
            return null;
        }
        return (fnbCombo, criteriaQuery, criteriaBuilder) -> criteriaBuilder.equal(fnbCombo.get("status"), status);
    }

    static Specification<FnbItem> itemNameFilter(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = toLikePattern(keyword);
        return (fnbItem, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(fnbItem.get("name")), pattern, '\\');
    }

    static Specification<FnbCombo> comboNameFilter(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = toLikePattern(keyword);
        return (fnbCombo, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(fnbCombo.get("name")), pattern, '\\');
    }

    private static String toLikePattern(String keyword) {
        String escapedKeyword = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escapedKeyword + "%";
    }
}
