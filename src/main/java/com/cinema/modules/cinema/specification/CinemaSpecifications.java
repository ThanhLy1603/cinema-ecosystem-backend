package com.cinema.modules.cinema.specification;

import com.cinema.modules.cinema.entity.Cinema;
import com.cinema.modules.cinema.enums.CinemaStatus;
import com.cinema.common.specification.SoftDeleteSpecifications;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.Locale;

public final class CinemaSpecifications {

    private CinemaSpecifications() {
    }

    public static Specification<Cinema> search(
            String keyword, String city, CinemaStatus status, Collection<Long> cinemaIds, boolean deleted) {
        Specification<Cinema> specification = Specification.where(SoftDeleteSpecifications.<Cinema>deletedIs(deleted));
        specification = specification.and(statusFilter(status));
        specification = specification.and(keywordFilter(keyword));
        specification = specification.and(cityFilter(city));
        specification = specification.and(cinemaIdFilter(cinemaIds));
        return specification;
    }

    static Specification<Cinema> statusFilter(CinemaStatus status) {
        if (status == null) {
            return null;
        }
        return (cinema, criteriaQuery, criteriaBuilder) -> criteriaBuilder.equal(cinema.get("status"), status);
    }

    static Specification<Cinema> keywordFilter(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        return (cinema, criteriaQuery, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(cinema.get("name")), pattern, '\\'),
                criteriaBuilder.like(criteriaBuilder.lower(cinema.get("address")), pattern, '\\'));
    }

    static Specification<Cinema> cityFilter(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        return (cinema, criteriaQuery, criteriaBuilder) -> criteriaBuilder.equal(cinema.get("city"), city.trim());
    }

    static Specification<Cinema> cinemaIdFilter(Collection<Long> cinemaIds) {
        if (cinemaIds == null) {
            return null;
        }
        return (cinema, criteriaQuery, criteriaBuilder) -> cinemaIds.isEmpty()
                ? criteriaBuilder.disjunction()
                : cinema.get("id").in(cinemaIds);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
