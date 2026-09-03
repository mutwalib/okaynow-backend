package com.okaynow.agencies.controller;

import com.okaynow.agencies.dto.AgencyMeResponse;
import com.okaynow.agencies.dto.AssignAgencyShiftRequest;
import com.okaynow.agencies.dto.BroadcastAgencyShiftRequest;
import com.okaynow.agencies.dto.BroadcastAgencyShiftResponse;
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
import com.okaynow.hiring.dto.CaregiverAgencyInterestResponse;
import com.okaynow.hiring.service.CaregiverAgencyInterestService;
import com.okaynow.payroll.dto.ClientInvoiceResponse;
import com.okaynow.payroll.dto.AgencySettingsResponse;
import com.okaynow.payroll.dto.UpdateAgencySettingsRequest;
import com.okaynow.payroll.service.AgencySettingsService;
import com.okaynow.payroll.service.InvoiceService;
import com.okaynow.roster.dto.AgencyRosterEntryResponse;
import com.okaynow.roster.dto.AgencyRosterMemberDetailResponse;
import com.okaynow.roster.dto.CaregiverLookupResponse;
import com.okaynow.roster.dto.InviteRosterCaregiverRequest;
import com.okaynow.roster.service.AgencyRosterService;
import com.okaynow.shiftrequests.dto.AgencyShiftRequestInboxResponse;
import com.okaynow.shiftrequests.dto.ShiftRequestResponse;
import com.okaynow.shiftrequests.service.ShiftRequestService;
import com.okaynow.shifts.dto.AgencyClientShiftRequest;
import com.okaynow.shifts.dto.CreateShiftRequest;
import com.okaynow.shifts.dto.CreateShiftResponse;
import com.okaynow.shifts.dto.ScheduleDayResponse;
import com.okaynow.shifts.dto.ShiftResponse;
import com.okaynow.shifts.service.ScheduleCalendarService;
import com.okaynow.shifts.service.ShiftService;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.FacilityProfile;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.FacilityProfileRepository;
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
    private final InvoiceService invoiceService;
    private final CaregiverAgencyInterestService interestService;
    private final AgencyRosterService agencyRosterService;
    private final ShiftRequestService shiftRequestService;
    private final AgencyShiftService agencyShiftService;
    private final UserService userService;
    private final ScheduleCalendarService scheduleCalendarService;
    private final ShiftService shiftService;
    private final ClientProfileRepository clientProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;

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

    @GetMapping("/invoices")
    public ResponseEntity<List<ClientInvoiceResponse>> invoices(Authentication authentication) {
        UUID agencyId = agencyAccessService.requireAgencyForUser(currentUserId(authentication)).getId();
        return ResponseEntity.ok(invoiceService.listForAgency(agencyId));
    }

    @PostMapping("/invoices/{invoiceId}/send")
    public ResponseEntity<ClientInvoiceResponse> sendInvoice(
            Authentication authentication,
            @PathVariable UUID invoiceId) {
        UUID userId = currentUserId(authentication);
        UUID agencyId = agencyAccessService.requireWritableAgencyId(userId);
        return ResponseEntity.ok(invoiceService.sendForAgency(
                agencyId, invoiceId, userService.getByEmail(authentication.getName())));
    }

    @GetMapping("/roster")
    public ResponseEntity<List<AgencyRosterEntryResponse>> roster(Authentication authentication) {
        return ResponseEntity.ok(agencyRosterService.listForAgency(currentUserId(authentication)));
    }

    @GetMapping("/caregivers/lookup")
    public ResponseEntity<CaregiverLookupResponse> lookupCaregiver(
            Authentication authentication,
            @RequestParam String email) {
        return ResponseEntity.ok(
                agencyRosterService.lookupByEmail(currentUserId(authentication), email));
    }

    @GetMapping("/caregiver-interests")
    public ResponseEntity<List<CaregiverAgencyInterestResponse>> caregiverInterests(
            Authentication authentication) {
        return ResponseEntity.ok(interestService.listForAgency(currentUserId(authentication)));
    }

    @PostMapping("/caregiver-interests/{interestId}/accept")
    public ResponseEntity<CaregiverAgencyInterestResponse> acceptInterest(
            Authentication authentication,
            @PathVariable UUID interestId) {
        return ResponseEntity.ok(
                interestService.accept(currentUserId(authentication), interestId));
    }

    @PostMapping("/caregiver-interests/{interestId}/decline")
    public ResponseEntity<CaregiverAgencyInterestResponse> declineInterest(
            Authentication authentication,
            @PathVariable UUID interestId) {
        return ResponseEntity.ok(
                interestService.decline(currentUserId(authentication), interestId));
    }

    @GetMapping("/roster/{rosterId}")
    public ResponseEntity<AgencyRosterMemberDetailResponse> rosterMember(
            Authentication authentication,
            @PathVariable UUID rosterId) {
        return ResponseEntity.ok(
                agencyRosterService.getMemberDetail(currentUserId(authentication), rosterId));
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

    @PostMapping("/roster/{rosterId}/reactivate")
    public ResponseEntity<AgencyRosterEntryResponse> reactivateRoster(
            Authentication authentication,
            @PathVariable UUID rosterId) {
        return ResponseEntity.ok(
                agencyRosterService.reactivate(currentUserId(authentication), rosterId));
    }

    @PostMapping("/roster/{rosterId}/remove")
    public ResponseEntity<AgencyRosterEntryResponse> removeRoster(
            Authentication authentication,
            @PathVariable UUID rosterId) {
        return ResponseEntity.ok(
                agencyRosterService.remove(currentUserId(authentication), rosterId));
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

    @PostMapping("/shifts/{shiftId}/broadcast")
    public ResponseEntity<BroadcastAgencyShiftResponse> broadcastShift(
            Authentication authentication,
            @PathVariable UUID shiftId,
            @RequestBody(required = false) BroadcastAgencyShiftRequest request) {
        BroadcastAgencyShiftRequest body = request != null
                ? request
                : new BroadcastAgencyShiftRequest(List.of());
        return ResponseEntity.ok(
                agencyShiftService.broadcast(currentUserId(authentication), shiftId, body));
    }

    @GetMapping("/schedule/calendar")
    public ResponseEntity<List<ScheduleDayResponse>> scheduleCalendar(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID clientProfileId,
            @RequestParam(required = false) UUID facilityProfileId) {
        return ResponseEntity.ok(scheduleCalendarService.agencyCalendar(
                from,
                to,
                clientProfileId,
                facilityProfileId,
                userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/clients/{clientProfileId}/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateShiftResponse createClientShift(
            Authentication authentication,
            @PathVariable UUID clientProfileId,
            @Valid @RequestBody AgencyClientShiftRequest body) {
        ClientProfile client = clientProfileRepository.findById(clientProfileId)
                .orElseThrow(() -> new com.okaynow.common.exception.ResourceNotFoundException(
                        "Client not found"));
        CreateShiftRequest request = new CreateShiftRequest(
                clientProfileId,
                null,
                body.requiredQualification(),
                body.date(),
                body.endDate(),
                body.startTime(),
                body.endTime(),
                client.getAddressLine(),
                client.getCity(),
                client.getState() != null ? client.getState() : "MA",
                client.getZip(),
                client.getLat(),
                client.getLng(),
                null,
                null,
                body.notes(),
                body.scheduleType(),
                body.requiredHeadcount(),
                body.assignFromRoster() != null ? body.assignFromRoster() : Boolean.FALSE);
        return shiftService.create(request, userService.getByEmail(authentication.getName()));
    }

    @PostMapping("/facilities/{facilityProfileId}/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateShiftResponse createFacilityShift(
            Authentication authentication,
            @PathVariable UUID facilityProfileId,
            @Valid @RequestBody AgencyClientShiftRequest body) {
        FacilityProfile facility = facilityProfileRepository.findById(facilityProfileId)
                .orElseThrow(() -> new com.okaynow.common.exception.ResourceNotFoundException(
                        "Facility not found"));
        CreateShiftRequest request = new CreateShiftRequest(
                null,
                facilityProfileId,
                body.requiredQualification(),
                body.date(),
                body.endDate(),
                body.startTime(),
                body.endTime(),
                facility.getAddressLine(),
                facility.getCity(),
                facility.getState() != null ? facility.getState() : "MA",
                facility.getZip(),
                facility.getLat(),
                facility.getLng(),
                null,
                null,
                body.notes(),
                body.scheduleType(),
                body.requiredHeadcount(),
                body.assignFromRoster() != null ? body.assignFromRoster() : Boolean.FALSE);
        return shiftService.create(request, userService.getByEmail(authentication.getName()));
    }

    private UUID currentUserId(Authentication authentication) {
        return userService.getByEmail(authentication.getName()).getId();
    }
}
