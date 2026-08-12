package com.okaynow.auth.repository;

import com.okaynow.auth.domain.AuthChallenge;
import com.okaynow.auth.domain.AuthChallengePurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthChallengeRepository extends JpaRepository<AuthChallenge, UUID> {

    Optional<AuthChallenge> findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String email, AuthChallengePurpose purpose);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuthChallenge c
            set c.consumedAt = :now
            where c.userId = :userId
              and c.purpose = :purpose
              and c.consumedAt is null
            """)
    int consumeOpenChallenges(
            @Param("userId") UUID userId,
            @Param("purpose") AuthChallengePurpose purpose,
            @Param("now") Instant now);
}
