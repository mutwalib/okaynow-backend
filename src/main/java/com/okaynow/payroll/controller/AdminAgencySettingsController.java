package com.okaynow.payroll.controller;

import com.okaynow.audit.domain.AuditAction;
import com.okaynow.audit.service.AuditLogService;
import com.okaynow.payroll.domain.AgencySettings;
import com.okaynow.payroll.dto.AgencySettingsResponse;
import com.okaynow.payroll.dto.UpdateAgencySettingsRequest;
import com.okaynow.payroll.service.AgencySettingsService;
import com.okaynow.users.domain.User;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/settings/agency")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAgencySettingsController {

    private static final UUID AGENCY_SETTINGS_ENTITY_ID =
            UUID.nameUUIDFromBytes(("agency-settings-" + AgencySettings.SINGLETON_ID).getBytes());

    private final AgencySettingsService agencySettingsService;
    private final UserService userService;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<AgencySettingsResponse> get() {
        return ResponseEntity.ok(agencySettingsService.getResponse());
    }

    @PutMapping
    public ResponseEntity<AgencySettingsResponse> update(
            @Valid @RequestBody UpdateAgencySettingsRequest request,
            Authentication authentication) {
        AgencySettingsResponse updated = agencySettingsService.update(request);
        User actor = userService.getByEmail(authentication.getName());
        auditLogService.record(actor, AuditAction.AGENCY_SETTINGS_UPDATED, "AGENCY_SETTINGS",
                AGENCY_SETTINGS_ENTITY_ID, null,
                "takePercent=%s defaultPayRate=%s period=%s startDay=%s".formatted(
                        updated.agencyTakePercent(),
                        updated.defaultPayRate(),
                        updated.payPeriodType(),
                        updated.periodStartDay()));
        return ResponseEntity.ok(updated);
    }
}
