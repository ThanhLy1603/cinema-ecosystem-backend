package com.cinema.modules.auth.repository;

import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.enums.UserStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(Long id);

    @EntityGraph(attributePaths = "roles")
    List<User> findWithRolesByIdIn(Collection<Long> ids);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    @Query("""
            select count(user) from User user join user.roles role
            where role.code = :roleCode and user.status = :status
            """)
    long countByRoleCodeAndStatus(@Param("roleCode") String roleCode, @Param("status") UserStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User user set user.loyaltyPoints = user.loyaltyPoints + :points where user.id = :userId")
    int addLoyaltyPoints(@Param("userId") Long userId, @Param("points") int points);

    @Query("select user.loyaltyPoints from User user where user.id = :userId")
    Optional<Integer> findLoyaltyPointsById(@Param("userId") Long userId);
}
