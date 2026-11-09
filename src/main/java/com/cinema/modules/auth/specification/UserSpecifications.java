package com.cinema.modules.auth.specification;

import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.entity.UserBranchAssignment;
import com.cinema.modules.auth.enums.UserStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.Locale;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> search(
            String keyword, String roleCode, UserStatus status, Collection<Long> cinemaIds) {
        Specification<User> specification = Specification.where(statusFilter(status));
        specification = specification.and(keywordFilter(keyword));
        specification = specification.and(roleFilter(roleCode));
        specification = specification.and(branchFilter(cinemaIds));
        return specification;
    }

    static Specification<User> statusFilter(UserStatus status) {
        return (user, criteriaQuery, criteriaBuilder) -> status == null
                ? criteriaBuilder.notEqual(user.get("status"), UserStatus.DELETED)
                : criteriaBuilder.equal(user.get("status"), status);
    }

    static Specification<User> keywordFilter(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        return (user, criteriaQuery, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(user.get("fullName")), pattern, '\\'),
                criteriaBuilder.like(criteriaBuilder.lower(user.get("email")), pattern, '\\'),
                criteriaBuilder.like(
                        criteriaBuilder.lower(criteriaBuilder.coalesce(user.<String>get("phone"), "")), pattern, '\\'));
    }

    static Specification<User> roleFilter(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        return (user, criteriaQuery, criteriaBuilder) -> {
            Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
            Root<User> subqueryUser = subquery.from(User.class);
            Join<User, Role> role = subqueryUser.join("roles");
            subquery.select(subqueryUser.<Long>get("id"))
                    .where(
                            criteriaBuilder.equal(subqueryUser.get("id"), user.get("id")),
                            criteriaBuilder.equal(role.get("code"), roleCode));
            return criteriaBuilder.exists(subquery);
        };
    }

    static Specification<User> branchFilter(Collection<Long> cinemaIds) {
        if (cinemaIds == null) {
            return null;
        }
        return (user, criteriaQuery, criteriaBuilder) -> {
            if (cinemaIds.isEmpty()) {
                return criteriaBuilder.disjunction();
            }
            Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
            Root<UserBranchAssignment> userBranchAssignment = subquery.from(UserBranchAssignment.class);
            Predicate sameUserPredicate =
                    criteriaBuilder.equal(userBranchAssignment.get("user").get("id"), user.get("id"));
            Predicate inBranchesPredicate = userBranchAssignment.get("cinemaId").in(cinemaIds);
            subquery.select(userBranchAssignment.<Long>get("id")).where(sameUserPredicate, inBranchesPredicate);
            return criteriaBuilder.exists(subquery);
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
