package com.okaynow.agencies.repository;

import com.okaynow.agencies.domain.Agency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgencyRepository extends JpaRepository<Agency, UUID> {

    Optional<Agency> findBySlug(String slug);

    Optional<Agency> findByStripeConnectAccountId(String stripeConnectAccountId);

    boolean existsBySlug(String slug);

    @Query("""
            SELECT a FROM Agency a
            WHERE a.directoryListed = true
              AND a.subscriptionStatus IN (
                com.okaynow.agencies.domain.SubscriptionStatus.ACTIVE,
                com.okaynow.agencies.domain.SubscriptionStatus.TRIAL)
            ORDER BY a.displayName ASC
            """)
    List<Agency> findDirectoryListed();
}
