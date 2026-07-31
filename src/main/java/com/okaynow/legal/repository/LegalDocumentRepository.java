package com.okaynow.legal.repository;

import com.okaynow.legal.domain.LegalDocument;
import com.okaynow.legal.domain.LegalDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, UUID> {

    Optional<LegalDocument> findFirstByDocumentTypeAndPublishedTrueOrderByVersionDesc(
            LegalDocumentType documentType);

    List<LegalDocument> findByDocumentTypeOrderByVersionDesc(LegalDocumentType documentType);

    Optional<LegalDocument> findByDocumentTypeAndVersion(LegalDocumentType documentType, int version);

    int countByDocumentType(LegalDocumentType documentType);
}
