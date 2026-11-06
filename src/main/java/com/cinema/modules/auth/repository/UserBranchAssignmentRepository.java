package com.cinema.modules.auth.repository;

import com.cinema.modules.auth.entity.UserBranchAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserBranchAssignmentRepository extends JpaRepository<UserBranchAssignment, Long> {

    @Query("""
            select userBranchAssignment.cinemaId from UserBranchAssignment userBranchAssignment
            where userBranchAssignment.user.id = :userId
            """)
    List<Long> findCinemaIdsByUserId(@Param("userId") Long userId);

    List<UserBranchAssignment> findByUserId(Long userId);

    @Query("""
            select userBranchAssignment from UserBranchAssignment userBranchAssignment
            where userBranchAssignment.user.id in :userIds
            """)
    List<UserBranchAssignment> findByUserIdIn(@Param("userIds") Collection<Long> userIds);

    boolean existsByUserIdAndCinemaId(Long userId, Long cinemaId);

    @Query("""
            select count(userBranchAssignment) > 0
            from UserBranchAssignment userBranchAssignment, UserBranchAssignment otherUserBranchAssignment
            where userBranchAssignment.user.id = :userId
              and otherUserBranchAssignment.user.id = :otherUserId
              and userBranchAssignment.cinemaId = otherUserBranchAssignment.cinemaId
            """)
    boolean existsSharedBranch(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserBranchAssignment userBranchAssignment where userBranchAssignment.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
