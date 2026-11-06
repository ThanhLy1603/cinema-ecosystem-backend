package com.cinema.modules.auth.repository;

import com.cinema.modules.auth.entity.UserIdentity;
import com.cinema.modules.auth.enums.AuthProvider;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    @EntityGraph(attributePaths = {"user", "user.roles"})
    Optional<UserIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    List<UserIdentity> findByUserId(Long userId);

    boolean existsByUserIdAndProvider(Long userId, AuthProvider provider);
}
