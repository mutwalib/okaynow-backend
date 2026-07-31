package com.okaynow.legal.repository;

import com.okaynow.legal.domain.LegalAcceptance;
import com.okaynow.legal.domain.LegalDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LegalAcceptanceRepository extends JpaRepository<LegalAcceptance, UUID> {

    Optional<LegalAcceptance> findByUserIdAndDocumentTypeAndDocumentVersion(
            UUID userId, LegalDocumentType documentType, int documentVersion);

    boolean existsByUserIdAndDocumentTypeAndDocumentVersion(
            UUID userId, LegalDocumentType documentType, int documentVersion);
}
