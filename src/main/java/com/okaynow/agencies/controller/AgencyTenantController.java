package com.okaynow.agencies.controller;

import com.okaynow.agencies.dto.AgencyMeResponse;
import com.okaynow.agencies.dto.CheckoutSessionResponse;
import com.okaynow.agencies.dto.CreateCheckoutSessionRequest;
import com.okaynow.agencies.dto.UpdateAgencyDirectoryProfileRequest;
import com.okaynow.agencies.service.AgencyService;
import com.okaynow.agencies.service.StripeBillingService;
import com.okaynow.connections.dto.HomeAgencyConnectionResponse;
import com.okaynow.connections.service.HomeAgencyConnectionService;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agencies/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENCY_ADMIN')")
public class AgencyTenantController {

    private final AgencyService agencyService;
    private final HomeAgencyConnectionService connectionService;
    private final StripeBillingService stripeBillingService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<AgencyMeResponse> me(Authentication authentication) {
        return ResponseEntity.ok(agencyService.getMe(currentUserId(authentication)));
    }

    @PatchMapping("/directory-profile")
    public ResponseEntity<AgencyMeResponse> updateDirectoryProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateAgencyDirectoryProfileRequest request) {
        return ResponseEntity.ok(
                agencyService.updateDirectoryProfile(currentUserId(authentication), request));
    }

    @GetMapping("/connections")
    public ResponseEntity<List<HomeAgencyConnectionResponse>> connections(Authentication authentication) {
        return ResponseEntity.ok(connectionService.listForAgency(currentUserId(authentication)));
    }

    @PostMapping("/connections/{connectionId}/accept")
    public ResponseEntity<HomeAgencyConnectionResponse> acceptConnection(
            Authentication authentication,
            @PathVariable UUID connectionId) {
        return ResponseEntity.ok(
                connectionService.acceptConnection(currentUserId(authentication), connectionId));
    }

    @PostMapping("/connections/{connectionId}/end")
    public ResponseEntity<HomeAgencyConnectionResponse> endConnection(
            Authentication authentication,
            @PathVariable UUID connectionId) {
        return ResponseEntity.ok(
                connectionService.endConnectionForAgency(currentUserId(authentication), connectionId));
    }

    @PostMapping("/billing/checkout")
    public ResponseEntity<CheckoutSessionResponse> checkout(
            Authentication authentication,
            @Valid @RequestBody CreateCheckoutSessionRequest request) {
        return ResponseEntity.ok(
                stripeBillingService.createCheckoutSession(currentUserId(authentication), request.plan()));
    }

    private UUID currentUserId(Authentication authentication) {
        return userService.getByEmail(authentication.getName()).getId();
    }
}
