package com.okaynow.marketplace.repository;

import com.okaynow.marketplace.domain.CaregiverCredential;
import com.okaynow.marketplace.domain.CredentialType;
import com.okaynow.marketplace.domain.CredentialVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaregiverCredentialRepository extends JpaRepository<CaregiverCredential, UUID> {

    List<CaregiverCredential> findByCaregiverProfileIdOrderByCredentialTypeAsc(UUID caregiverProfileId);

    Optional<CaregiverCredential> findByCaregiverProfileIdAndCredentialType(
            UUID caregiverProfileId, CredentialType credentialType);

    @Query("""
            select count(c) from CaregiverCredential c
            where c.verificationStatus = :status
              and c.expiryDate is not null
              and c.expiryDate >= :from
              and c.expiryDate <= :to
            """)
    long countExpiringBetween(
            @Param("status") CredentialVerificationStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
