package com.githubProjects.authShield.repository;

import com.githubProjects.authShield.dto.OtpPurpose;
import com.githubProjects.authShield.entity.OtpChallenge;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from OtpChallenge challenge where challenge.user.id = :userId "
            + "and challenge.purpose = :purpose and challenge.consumedAt is null "
            + "order by challenge.createdAt desc")
    List<OtpChallenge> findLatestUnconsumedForUpdate(
            @Param("userId") Long userId, @Param("purpose") OtpPurpose purpose,
            org.springframework.data.domain.Pageable pageable);

    @Query("select challenge from OtpChallenge challenge where challenge.user.id = :userId "
            + "and challenge.purpose = :purpose and challenge.createdAt >= :createdAfter")
    List<OtpChallenge> findCreatedSince(@Param("userId") Long userId,
            @Param("purpose") OtpPurpose purpose, @Param("createdAfter") Instant createdAfter);
}