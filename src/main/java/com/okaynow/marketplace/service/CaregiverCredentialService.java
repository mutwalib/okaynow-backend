package com.okaynow.marketplace.service;

import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.marketplace.credentialing.PrimarySourceCredentialVerifier;
import com.okaynow.marketplace.domain.CaregiverCredential;
import com.okaynow.marketplace.domain.CredentialVerificationStatus;
import com.okaynow.marketplace.dto.CaregiverCredentialResponse;
import com.okaynow.marketplace.dto.UpsertCaregiverCredentialRequest;
import com.okaynow.marketplace.repository.CaregiverCredentialRepository;
import com.okaynow.users.repository.CaregiverProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaregiverCredentialService {

    private final CaregiverCredentialRepository credentialRepository;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final PrimarySourceCredentialVerifier primarySourceVerifier;

    @Transactional(readOnly = true)
    public List<CaregiverCredentialResponse> listForCaregiver(UUID caregiverProfileId) {
        assertCaregiverExists(caregiverProfileId);
        return credentialRepository
                .findByCaregiverProfileIdOrderByCredentialTypeAsc(caregiverProfileId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CaregiverCredentialResponse upsert(
            UUID caregiverProfileId,
            UpsertCaregiverCredentialRequest request,
            UUID reviewerUserId) {
        assertCaregiverExists(caregiverProfileId);
        CaregiverCredential cred = credentialRepository
                .findByCaregiverProfileIdAndCredentialType(
                        caregiverProfileId, request.credentialType())
                .orElseGet(() -> CaregiverCredential.builder()
                        .caregiverProfileId(caregiverProfileId)
                        .credentialType(request.credentialType())
                        .build());

        cred.setLicenseNumber(request.licenseNumber());
        cred.setIssueDate(request.issueDate());
        cred.setExpiryDate(request.expiryDate());
        cred.setDocumentUrl(request.documentUrl());
        CredentialVerificationStatus status = request.verificationStatus() != null
                ? request.verificationStatus()
                : CredentialVerificationStatus.PENDING;
        cred.setVerificationStatus(status);
        if (status == CredentialVerificationStatus.APPROVED
                || status == CredentialVerificationStatus.REJECTED) {
            cred.setReviewedBy(reviewerUserId);
            cred.setReviewedAt(Instant.now());
        }
        return toResponse(credentialRepository.save(cred));
    }

    @Transactional
    public CaregiverCredentialResponse runPrimarySourceCheck(UUID credentialId) {
        CaregiverCredential cred = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new ResourceNotFoundException("Credential not found"));
        var result = primarySourceVerifier.verify(cred);
        cred.setPrimarySourceStatus(result.status());
        cred.setPrimarySourceNotes(result.notes());
        cred.setPrimarySourceCheckedAt(Instant.now());
        return toResponse(credentialRepository.save(cred));
    }

    private void assertCaregiverExists(UUID caregiverProfileId) {
        if (!caregiverProfileRepository.existsById(caregiverProfileId)) {
            throw new ResourceNotFoundException("Caregiver not found");
        }
    }

    private CaregiverCredentialResponse toResponse(CaregiverCredential c) {
        return new CaregiverCredentialResponse(
                c.getId(),
                c.getCaregiverProfileId(),
                c.getCredentialType(),
                c.getLicenseNumber(),
                c.getIssueDate(),
                c.getExpiryDate(),
                c.getDocumentUrl(),
                c.getVerificationStatus(),
                c.getPrimarySourceStatus(),
                c.getPrimarySourceCheckedAt(),
                c.getPrimarySourceNotes(),
                c.getReviewedBy(),
                c.getReviewedAt(),
                c.getCreatedAt());
    }
}
