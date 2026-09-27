package com.microservice.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.microservice.user.entity.RefreshSession;

import jakarta.persistence.LockModeType;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

    Optional<RefreshSession> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select rs
            from RefreshSession rs
            where rs.tokenHash = :tokenHash
            """)
    Optional<RefreshSession> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select rs
            from RefreshSession rs
            where rs.familyId = :familyId
            """)
    List<RefreshSession> findAllByFamilyIdForUpdate(
            @Param("familyId") UUID familyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select rs
            from RefreshSession rs
            where rs.user.userId = :userId
              and rs.revokedAt is null
            """)
    List<RefreshSession> findAllActiveByUserIdForUpdate(
            @Param("userId") UUID userId);
}
