package com.okaynow.agencies.controller;

import com.okaynow.agencies.dto.SuperAdminAgencyResponse;
import com.okaynow.agencies.dto.SuperAdminUpdateSubscriptionRequest;
import com.okaynow.agencies.service.AgencyService;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/super/agencies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SuperAdminAgencyController {

    private final AgencyService agencyService;
    private final AgencyAccessService agencyAccessService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<SuperAdminAgencyResponse>> list(Authentication authentication) {
        agencyAccessService.requireSuperAdmin(currentUserId(authentication));
        return ResponseEntity.ok(agencyService.listAllForSuperAdmin());
    }

    @PatchMapping("/{agencyId}/subscription")
    public ResponseEntity<SuperAdminAgencyResponse> updateSubscription(
            Authentication authentication,
            @PathVariable UUID agencyId,
            @Valid @RequestBody SuperAdminUpdateSubscriptionRequest request) {
        agencyAccessService.requireSuperAdmin(currentUserId(authentication));
        return ResponseEntity.ok(agencyService.updateSubscriptionForSuperAdmin(agencyId, request));
    }

    private UUID currentUserId(Authentication authentication) {
        return userService.getByEmail(authentication.getName()).getId();
    }
}
