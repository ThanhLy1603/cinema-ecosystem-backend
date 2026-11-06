package com.cinema.modules.auth.repository;

import com.cinema.modules.auth.entity.UserToken;
import com.cinema.modules.auth.enums.UserTokenType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select userToken from UserToken userToken
            where userToken.tokenHash = :tokenHash and userToken.tokenType = :userTokenType
            """)
    Optional<UserToken> findByTokenHashAndTypeForUpdate(
            @Param("tokenHash") String tokenHash, @Param("userTokenType") UserTokenType userTokenType);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update UserToken userToken set userToken.usedAt = :now
            where userToken.user.id = :userId
              and userToken.tokenType = :userTokenType
              and userToken.usedAt is null
            """)
    int invalidateUnused(
            @Param("userId") Long userId,
            @Param("userTokenType") UserTokenType userTokenType,
            @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserToken userToken where userToken.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
