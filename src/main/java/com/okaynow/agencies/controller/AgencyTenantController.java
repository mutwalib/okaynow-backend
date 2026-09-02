package com.okaynow.agencies.controller;

import com.okaynow.agencies.dto.AgencyMeResponse;
import com.okaynow.agencies.dto.AssignAgencyShiftRequest;
import com.okaynow.agencies.dto.CheckoutSessionResponse;
import com.okaynow.agencies.dto.ConnectOnboardingResponse;
import com.okaynow.agencies.dto.ConnectStatusResponse;
import com.okaynow.agencies.dto.CreateCheckoutSessionRequest;
import com.okaynow.agencies.dto.UpdateAgencyDirectoryProfileRequest;
import com.okaynow.agencies.service.AgencyHoursExportService;
import com.okaynow.agencies.service.AgencyService;
import com.okaynow.agencies.service.AgencyShiftService;
import com.okaynow.agencies.service.StripeBillingService;
import com.okaynow.agencies.service.StripeConnectService;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.booking.dto.ShiftClaimResponse;
import com.okaynow.connections.dto.HomeAgencyConnectionResponse;
import com.okaynow.connections.service.HomeAgencyConnectionService;
import com.okaynow.payroll.dto.AgencySettingsResponse;
import com.okaynow.payroll.dto.UpdateAgencySettingsRequest;
import com.okaynow.payroll.service.AgencySettingsService;
import com.okaynow.roster.dto.AgencyRosterEntryResponse;
import com.okaynow.roster.dto.InviteRosterCaregiverRequest;
import com.okaynow.roster.service.AgencyRosterService;
import com.okaynow.shiftrequests.dto.AgencyShiftRequestInboxResponse;
import com.okaynow.shiftrequests.dto.ShiftRequestResponse;
import com.okaynow.shiftrequests.service.ShiftRequestService;
import com.okaynow.shifts.dto.ShiftResponse;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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
    private final StripeConnectService stripeConnectService;
    private final AgencySettingsService agencySettingsService;
    private final AgencyAccessService agencyAccessService;
    private final AgencyHoursExportService agencyHoursExportService;
    private final AgencyRosterService agencyRosterService;
    private final ShiftRequestService shiftRequestService;
    private final AgencyShiftService agencyShiftService;
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

    @GetMapping("/billing/connect")
    public ResponseEntity<ConnectStatusResponse> connectStatus(Authentication authentication) {
        return ResponseEntity.ok(stripeConnectService.status(currentUserId(authentication)));
    }

    @PostMapping("/billing/connect/onboard")
    public ResponseEntity<ConnectOnboardingResponse> connectOnboard(Authentication authentication) {
        return ResponseEntity.ok(
                stripeConnectService.createOnboardingLink(currentUserId(authentication)));
    }

    @GetMapping("/settings")
    public ResponseEntity<AgencySettingsResponse> settings(Authentication authentication) {
        UUID agencyId = agencyAccessService.requireAgencyForUser(currentUserId(authentication)).getId();
        return ResponseEntity.ok(agencySettingsService.getResponseForAgency(agencyId));
    }

    @PutMapping("/settings")
    public ResponseEntity<AgencySettingsResponse> updateSettings(
            Authentication authentication,
            @Valid @RequestBody UpdateAgencySettingsRequest request) {
        UUID agencyId = agencyAccessService.requireWritableAgencyId(currentUserId(authentication));
        return ResponseEntity.ok(agencySettingsService.updateForAgency(agencyId, request));
    }

    @GetMapping("/payroll/export")
    public ResponseEntity<byte[]> payrollExport(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] csv = agencyHoursExportService.csv(currentUserId(authentication), from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"agency-hours-" + from + "-to-" + to + ".csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(csv);
    }

    @GetMapping("/roster")
    public ResponseEntity<List<AgencyRosterEntryResponse>> roster(Authentication authentication) {
        return ResponseEntity.ok(agencyRosterService.listForAgency(currentUserId(authentication)));
    }

    @PostMapping("/roster/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public AgencyRosterEntryResponse inviteRoster(
            Authentication authentication,
            @Valid @RequestBody InviteRosterCaregiverRequest request) {
        return agencyRosterService.invite(currentUserId(authentication), request);
    }

    @PostMapping("/roster/{rosterId}/suspend")
    public ResponseEntity<AgencyRosterEntryResponse> suspendRoster(
            Authentication authentication,
            @PathVariable UUID rosterId) {
        return ResponseEntity.ok(
                agencyRosterService.suspend(currentUserId(authentication), rosterId));
    }

    @GetMapping("/shift-requests")
    public ResponseEntity<List<AgencyShiftRequestInboxResponse>> shiftRequestInbox(
            Authentication authentication) {
        return ResponseEntity.ok(shiftRequestService.inboxForAgency(currentUserId(authentication)));
    }

    @PostMapping("/shift-requests/{inboxId}/accept")
    public ResponseEntity<ShiftRequestResponse> acceptShiftRequest(
            Authentication authentication,
            @PathVariable UUID inboxId) {
        return ResponseEntity.ok(
                shiftRequestService.acceptForAgency(currentUserId(authentication), inboxId));
    }

    @PostMapping("/shift-requests/{inboxId}/decline")
    public ResponseEntity<AgencyShiftRequestInboxResponse> declineShiftRequest(
            Authentication authentication,
            @PathVariable UUID inboxId) {
        return ResponseEntity.ok(
                shiftRequestService.declineForAgency(currentUserId(authentication), inboxId));
    }

    @GetMapping("/shifts")
    public ResponseEntity<List<ShiftResponse>> shifts(Authentication authentication) {
        return ResponseEntity.ok(agencyShiftService.listForAgency(currentUserId(authentication)));
    }

    @PostMapping("/shifts/{shiftId}/assign")
    public ResponseEntity<ShiftClaimResponse> assignShift(
            Authentication authentication,
            @PathVariable UUID shiftId,
            @Valid @RequestBody AssignAgencyShiftRequest request) {
        return ResponseEntity.ok(
                agencyShiftService.assign(currentUserId(authentication), shiftId, request));
    }

    private UUID currentUserId(Authentication authentication) {
        return userService.getByEmail(authentication.getName()).getId();
    }
}
