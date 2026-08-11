package com.okaynow.marketplace.controller;

import com.okaynow.audit.domain.AuditAction;
import com.okaynow.audit.service.AuditLogService;
import com.okaynow.marketplace.dto.CaregiverCredentialResponse;
import com.okaynow.marketplace.dto.UpsertCaregiverCredentialRequest;
import com.okaynow.marketplace.service.CaregiverCredentialService;
import com.okaynow.users.domain.User;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/caregivers/{caregiverProfileId}/credentials")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCaregiverCredentialController {

    private final CaregiverCredentialService credentialService;
    private final UserService userService;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<List<CaregiverCredentialResponse>> list(
            @PathVariable UUID caregiverProfileId) {
        return ResponseEntity.ok(credentialService.listForCaregiver(caregiverProfileId));
    }

    @PutMapping
    public ResponseEntity<CaregiverCredentialResponse> upsert(
            @PathVariable UUID caregiverProfileId,
            @Valid @RequestBody UpsertCaregiverCredentialRequest request,
            Authentication authentication) {
        User actor = userService.getByEmail(authentication.getName());
        CaregiverCredentialResponse saved =
                credentialService.upsert(caregiverProfileId, request, actor.getId());
        auditLogService.record(actor, AuditAction.CREDENTIAL_UPSERTED, "CREDENTIAL",
                saved.id(), null,
                "caregiver=%s type=%s status=%s".formatted(
                        caregiverProfileId, saved.credentialType(), saved.verificationStatus()));
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{credentialId}/primary-source-check")
    public ResponseEntity<CaregiverCredentialResponse> primarySourceCheck(
            @PathVariable UUID caregiverProfileId,
            @PathVariable UUID credentialId,
            Authentication authentication) {
        CaregiverCredentialResponse result = credentialService.runPrimarySourceCheck(credentialId);
        User actor = userService.getByEmail(authentication.getName());
        auditLogService.record(actor, AuditAction.CREDENTIAL_PRIMARY_SOURCE_CHECKED, "CREDENTIAL",
                credentialId, null,
                "caregiver=%s status=%s".formatted(caregiverProfileId, result.primarySourceStatus()));
        return ResponseEntity.ok(result);
    }
}
