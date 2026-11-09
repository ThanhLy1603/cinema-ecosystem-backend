package com.cinema.common.specification;

import com.cinema.common.entity.interfaces.ISoftDelete;
import org.springframework.data.jpa.domain.Specification;

public final class SoftDeleteSpecifications {

    private SoftDeleteSpecifications() {
    }

    public static <T extends ISoftDelete> Specification<T> deletedIs(boolean deleted) {
        return (softDeletable, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(softDeletable.get("deleted"), deleted);
    }
}
