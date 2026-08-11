package com.okaynow.marketplace.controller;

import com.okaynow.audit.domain.AuditAction;
import com.okaynow.audit.service.AuditLogService;
import com.okaynow.marketplace.dto.QualificationRulePackResponse;
import com.okaynow.marketplace.dto.UpdateQualificationRulePackRequest;
import com.okaynow.marketplace.service.QualificationRulePackService;
import com.okaynow.users.domain.Qualification;
import com.okaynow.users.domain.User;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/marketplace/rule-packs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMarketplaceRulesController {

    private final QualificationRulePackService rulePackService;
    private final UserService userService;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<List<QualificationRulePackResponse>> list() {
        return ResponseEntity.ok(rulePackService.listAll());
    }

    @PutMapping("/{qualification}")
    public ResponseEntity<QualificationRulePackResponse> update(
            @PathVariable Qualification qualification,
            @Valid @RequestBody UpdateQualificationRulePackRequest request,
            Authentication authentication) {
        QualificationRulePackResponse updated = rulePackService.update(qualification, request);
        User actor = userService.getByEmail(authentication.getName());
        auditLogService.record(actor, AuditAction.MARKETPLACE_RULE_PACK_UPDATED, "RULE_PACK",
                updated.id(), null,
                "qualification=%s matching=%s surge=%s enforceCreds=%s".formatted(
                        updated.qualification(),
                        updated.matchingMode(),
                        updated.surgeEligible(),
                        updated.enforceCredentials()));
        return ResponseEntity.ok(updated);
    }
}
