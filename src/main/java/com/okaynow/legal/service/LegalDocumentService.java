package com.okaynow.legal.service;

import com.okaynow.audit.domain.AuditAction;
import com.okaynow.audit.service.AuditLogService;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.legal.domain.LegalAcceptance;
import com.okaynow.legal.domain.LegalDocument;
import com.okaynow.legal.domain.LegalDocumentType;
import com.okaynow.legal.dto.AcceptLegalDocumentsRequest;
import com.okaynow.legal.dto.LegalAcceptanceStatusResponse;
import com.okaynow.legal.dto.LegalDocumentResponse;
import com.okaynow.legal.dto.UpsertLegalDocumentRequest;
import com.okaynow.legal.repository.LegalAcceptanceRepository;
import com.okaynow.legal.repository.LegalDocumentRepository;
import com.okaynow.users.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Order(5)
public class LegalDocumentService implements ApplicationRunner {

    private final LegalDocumentRepository documentRepository;
    private final LegalAcceptanceRepository acceptanceRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIfMissing(LegalDocumentType.TERMS_OF_SERVICE, "Terms of Service", DEFAULT_TERMS);
        seedIfMissing(LegalDocumentType.PRIVACY_POLICY, "Privacy Policy", DEFAULT_PRIVACY);
        seedIfMissing(LegalDocumentType.PLATFORM_POLICY, "Platform Policy", DEFAULT_PLATFORM);
        // One-time bump when seeded docs predate caregiver transport/wellbeing language.
        publishIfMissingPhrase(
                LegalDocumentType.TERMS_OF_SERVICE,
                "Terms of Service",
                DEFAULT_TERMS,
                "responsible for their own transportation");
        publishIfMissingPhrase(
                LegalDocumentType.PLATFORM_POLICY,
                "Platform Policy",
                DEFAULT_PLATFORM,
                "responsible for their own transportation");
    }

    private void seedIfMissing(LegalDocumentType type, String title, String body) {
        if (documentRepository.countByDocumentType(type) > 0) {
            return;
        }
        LegalDocument doc = LegalDocument.builder()
                .documentType(type)
                .version(1)
                .title(title)
                .body(body)
                .published(true)
                .publishedAt(Instant.now())
                .build();
        documentRepository.save(doc);
    }

    private void publishIfMissingPhrase(
            LegalDocumentType type, String title, String body, String requiredPhrase) {
        var current = documentRepository
                .findFirstByDocumentTypeAndPublishedTrueOrderByVersionDesc(type);
        if (current.isEmpty()) {
            return;
        }
        if (current.get().getBody() != null
                && current.get().getBody().contains(requiredPhrase)) {
            return;
        }
        for (LegalDocument prior : documentRepository.findByDocumentTypeOrderByVersionDesc(type)) {
            if (prior.isPublished()) {
                prior.setPublished(false);
            }
        }
        int nextVersion = documentRepository.countByDocumentType(type) + 1;
        documentRepository.save(LegalDocument.builder()
                .documentType(type)
                .version(nextVersion)
                .title(title)
                .body(body)
                .published(true)
                .publishedAt(Instant.now())
                .build());
    }

    @Transactional(readOnly = true)
    public LegalDocumentResponse current(LegalDocumentType type) {
        return documentRepository.findFirstByDocumentTypeAndPublishedTrueOrderByVersionDesc(type)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No published " + type));
    }

    @Transactional(readOnly = true)
    public List<LegalDocumentResponse> listCurrent() {
        List<LegalDocumentResponse> out = new ArrayList<>();
        for (LegalDocumentType type : LegalDocumentType.values()) {
            documentRepository.findFirstByDocumentTypeAndPublishedTrueOrderByVersionDesc(type)
                    .map(this::toResponse)
                    .ifPresent(out::add);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<LegalDocumentResponse> history(LegalDocumentType type) {
        return documentRepository.findByDocumentTypeOrderByVersionDesc(type).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LegalDocumentResponse upsert(UpsertLegalDocumentRequest request, User actor) {
        int nextVersion = documentRepository.countByDocumentType(request.documentType()) + 1;
        LegalDocument doc = LegalDocument.builder()
                .documentType(request.documentType())
                .version(nextVersion)
                .title(request.title().trim())
                .body(request.body().trim())
                .published(request.publish())
                .publishedAt(request.publish() ? Instant.now() : null)
                .publishedBy(request.publish() ? actor.getId() : null)
                .build();
        doc = documentRepository.save(doc);
        if (request.publish()) {
            // Unpublish prior versions so only latest is active.
            for (LegalDocument prior : documentRepository.findByDocumentTypeOrderByVersionDesc(request.documentType())) {
                if (!prior.getId().equals(doc.getId()) && prior.isPublished()) {
                    prior.setPublished(false);
                }
            }
            auditLogService.record(actor, AuditAction.LEGAL_DOCUMENT_PUBLISHED, "LEGAL_DOCUMENT",
                    doc.getId(), null,
                    "type=%s version=%s".formatted(doc.getDocumentType(), doc.getVersion()));
        }
        return toResponse(doc);
    }

    @Transactional(readOnly = true)
    public LegalAcceptanceStatusResponse acceptanceStatus(User user) {
        List<LegalDocumentResponse> pending = new ArrayList<>();
        for (LegalDocumentType type : EnumSet.allOf(LegalDocumentType.class)) {
            LegalDocument current = documentRepository
                    .findFirstByDocumentTypeAndPublishedTrueOrderByVersionDesc(type)
                    .orElse(null);
            if (current == null) {
                continue;
            }
            boolean accepted = acceptanceRepository.existsByUserIdAndDocumentTypeAndDocumentVersion(
                    user.getId(), type, current.getVersion());
            if (!accepted) {
                pending.add(toResponse(current));
            }
        }
        return new LegalAcceptanceStatusResponse(pending.isEmpty(), pending);
    }

    @Transactional
    public LegalAcceptanceStatusResponse accept(AcceptLegalDocumentsRequest request, User user) {
        for (UUID documentId : request.documentIds()) {
            LegalDocument doc = documentRepository.findById(documentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Legal document not found"));
            if (!doc.isPublished()) {
                throw new BadRequestException("Only published documents can be accepted");
            }
            if (!acceptanceRepository.existsByUserIdAndDocumentTypeAndDocumentVersion(
                    user.getId(), doc.getDocumentType(), doc.getVersion())) {
                acceptanceRepository.save(LegalAcceptance.builder()
                        .userId(user.getId())
                        .documentId(doc.getId())
                        .documentType(doc.getDocumentType())
                        .documentVersion(doc.getVersion())
                        .build());
            }
        }
        auditLogService.record(user, AuditAction.LEGAL_TERMS_ACCEPTED, "USER",
                user.getId(), null,
                "accepted=%s".formatted(request.documentIds().size()));
        return acceptanceStatus(user);
    }

    @Transactional
    public void acceptCurrentAll(User user) {
        List<UUID> ids = listCurrent().stream().map(LegalDocumentResponse::id).toList();
        if (!ids.isEmpty()) {
            accept(new AcceptLegalDocumentsRequest(ids), user);
        }
    }

    @Transactional(readOnly = true)
    public void assertAcceptedCurrent(User user) {
        LegalAcceptanceStatusResponse status = acceptanceStatus(user);
        if (!status.upToDate()) {
            throw new BadRequestException(
                    "You must accept the latest Terms of Service, Privacy Policy, and Platform Policy");
        }
    }

    private LegalDocumentResponse toResponse(LegalDocument doc) {
        return new LegalDocumentResponse(
                doc.getId(),
                doc.getDocumentType(),
                doc.getVersion(),
                doc.getTitle(),
                doc.getBody(),
                doc.isPublished(),
                doc.getPublishedAt(),
                doc.getCreatedAt());
    }

    private static final String DEFAULT_TERMS = """
            OkayNow Terms of Service (Massachusetts)

            1. Platform role. OkayNow connects caregivers with families and facilities for home care shifts. Caregivers are engaged as W-2 employees of the staffing agency operating OkayNow unless otherwise stated in writing.

            2. Accurate scheduling and attendance. Clients and facilities must not falsely report caregiver no-shows. If a caregiver has clocked in, or arrival has been confirmed, a no-show cannot be recorded through the platform. Disputes must be raised with the agency.

            3. Caregiver transportation and wellbeing. Caregivers are responsible for their own transportation to and from client homes and for their own wellbeing while traveling to and while working in the client's home. OkayNow and the client do not provide or guarantee transportation, and caregivers should take reasonable steps to keep themselves safe and fit for duty.

            4. Off-platform hiring (conversion). If you connect with a caregiver through OkayNow and then hire or continue that caregiver privately outside the platform for ongoing care, you agree to pay the Platform Conversion Fee set in Agency Settings (shown on your rate card). The fee is invoiced when you report the conversion or when the agency discovers it.

            5. Fees and invoices. Rejection fees, conversion fees, and shift bill rates are due as invoiced. Non-payment may result in suspension of account access.

            6. Updates. OkayNow may publish updated Terms. Continued use after publication of a new version requires your acceptance of that version.
            """;

    private static final String DEFAULT_PRIVACY = """
            OkayNow Privacy Policy

            We collect account, profile, scheduling, visit (including GPS clock-in/out where required), and billing information to operate the staffing marketplace and meet Massachusetts care and EVV obligations.

            We do not sell personal information. Access is role-restricted. Visit and client address data are treated as sensitive.

            Contact the agency to request access or correction of your personal data.
            """;

    private static final String DEFAULT_PLATFORM = """
            OkayNow Platform Policy

            - Do not solicit or arrange private payment to bypass OkayNow for caregivers introduced on the platform without paying the Platform Conversion Fee.
            - Do not falsify attendance, clock times, or no-show reports.
            - Caregivers must clock in/out as required; clients should confirm arrival when accurate.
            - Caregivers are responsible for their own transportation to and from the client's home and for their wellbeing while traveling to and while providing care in the home.
            - Violations may result in fees, suspension, or termination of access.
            """;
}
