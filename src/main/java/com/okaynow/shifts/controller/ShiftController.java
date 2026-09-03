package com.okaynow.shifts.controller;

import com.okaynow.booking.dto.AssignedCaregiverResponse;
import com.okaynow.booking.dto.ClientRejectCaregiverResponse;
import com.okaynow.booking.dto.RejectCaregiverRequest;
import com.okaynow.booking.service.BookingService;
import com.okaynow.common.dto.PagedResponse;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.domain.DayPeriod;
import com.okaynow.shifts.dto.CreateShiftRequest;
import com.okaynow.shifts.dto.CreateShiftResponse;
import com.okaynow.shifts.dto.RequestReplacementRequest;
import com.okaynow.shifts.dto.ShiftResponse;
import com.okaynow.shifts.dto.UpdateShiftRequest;
import com.okaynow.shifts.dto.UpdatePlatformPaymentRequest;
import com.okaynow.shifts.service.ShiftService;
import com.okaynow.users.domain.Qualification;
import com.okaynow.users.domain.Role;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shiftService;
    private final BookingService bookingService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<PagedResponse<ShiftResponse>> search(
            @RequestParam(required = false) ShiftStatus status,
            @RequestParam(required = false) Qualification qualification,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) UUID clientProfileId,
            @RequestParam(required = false) UUID facilityProfileId,
            @RequestParam(required = false) BigDecimal minPay,
            @RequestParam(required = false) BigDecimal maxPay,
            @RequestParam(required = false) DayPeriod dayPeriod,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        var actor = userService.getByEmail(authentication.getName());
        // Admins: newest shift date first. Caregivers/clients: soonest first.
        Sort sort = actor.getRole() == Role.ADMIN
                ? Sort.by(Sort.Direction.DESC, "date").and(Sort.by(Sort.Direction.DESC, "startTime"))
                : Sort.by(Sort.Direction.ASC, "date").and(Sort.by(Sort.Direction.ASC, "startTime"));
        return ResponseEntity.ok(shiftService.search(status, qualification, dateFrom, dateTo,
                clientProfileId, facilityProfileId, minPay, maxPay, dayPeriod,
                PageRequest.of(page, Math.min(size, 100), sort), actor));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShiftResponse> getById(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(shiftService.getById(
                id, userService.getByEmail(authentication.getName())));
    }

    @GetMapping("/{id}/assigned-caregivers")
    @PreAuthorize("hasAnyRole('CLIENT', 'FACILITY', 'ADMIN')")
    public ResponseEntity<List<AssignedCaregiverResponse>> assignedCaregivers(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(shiftService.assignedCaregivers(
                id, userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/{id}/request-replacement")
    @PreAuthorize("hasAnyRole('CLIENT', 'FACILITY', 'ADMIN')")
    public ResponseEntity<ShiftResponse> requestReplacement(
            @PathVariable UUID id,
            @RequestBody(required = false) RequestReplacementRequest request,
            Authentication authentication) {
        String reason = request != null ? request.reason() : null;
        Integer slots = request != null ? request.slots() : null;
        return ResponseEntity.ok(bookingService.requestReplacement(
                id, reason, slots, userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/{id}/close-marketplace")
    @PreAuthorize("hasAnyRole('CLIENT', 'FACILITY', 'ADMIN')")
    public ResponseEntity<ShiftResponse> closeMarketplace(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(bookingService.closeMarketplace(
                id, userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/{id}/claims/{claimId}/reject")
    @PreAuthorize("hasAnyRole('CLIENT', 'FACILITY')")
    public ResponseEntity<ClientRejectCaregiverResponse> rejectCaregiver(
            @PathVariable UUID id,
            @PathVariable UUID claimId,
            @RequestBody(required = false) RejectCaregiverRequest request,
            Authentication authentication) {
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(bookingService.rejectCaregiverByClient(
                id,
                claimId,
                reason,
                userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasAnyRole('CLIENT', 'FACILITY', 'ADMIN')")
    public ResponseEntity<ShiftResponse> markNoShow(
            @PathVariable UUID id,
            @RequestBody(required = false) RejectCaregiverRequest request,
            Authentication authentication) {
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(bookingService.markNoShow(
                id, reason, userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/platform-conversion")
    @PreAuthorize("hasAnyRole('CLIENT', 'FACILITY')")
    public ResponseEntity<com.okaynow.payroll.dto.ClientInvoiceResponse> reportPlatformConversion(
            @Valid @RequestBody com.okaynow.booking.dto.PlatformConversionRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                bookingService.reportPlatformConversion(
                        request.caregiverProfileId(),
                        request.notes(),
                        userService.getByEmail(authentication.getName())));
    }

    @GetMapping("/platform-conversion/reported-caregivers")
    @PreAuthorize("hasAnyRole('CLIENT', 'FACILITY')")
    public ResponseEntity<List<java.util.UUID>> reportedConversionCaregivers(
            Authentication authentication) {
        return ResponseEntity.ok(bookingService.listReportedConversionCaregiverIds(
                userService.getByEmail(authentication.getName())));
    }

    @GetMapping("/platform-conversion/caregivers")
    @PreAuthorize("hasAnyRole('CLIENT', 'FACILITY')")
    public ResponseEntity<List<com.okaynow.booking.dto.PlatformConnectedCaregiverResponse>>
            connectedConversionCaregivers(Authentication authentication) {
        return ResponseEntity.ok(bookingService.listConnectedCaregiversForConversion(
                userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/{id}/assign-from-roster")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<com.okaynow.booking.dto.ShiftClaimResponse> assignFromRoster(
            @PathVariable UUID id,
            @Valid @RequestBody com.okaynow.booking.dto.AssignFromRosterRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(bookingService.assignFromClientRoster(
                id,
                request.caregiverProfileId(),
                userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/{id}/invite")
    @PreAuthorize("hasAnyRole('CLIENT', 'FACILITY')")
    public ResponseEntity<com.okaynow.booking.dto.ShiftClaimResponse> inviteCaregiver(
            @PathVariable UUID id,
            @Valid @RequestBody com.okaynow.booking.dto.InviteCaregiverRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.invite(
                id,
                request.caregiverProfileId(),
                userService.getByEmail(authentication.getName())));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FACILITY', 'CLIENT')")
    public ResponseEntity<CreateShiftResponse> create(@Valid @RequestBody CreateShiftRequest request,
                                                      Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                shiftService.create(request, userService.getByEmail(authentication.getName())));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACILITY', 'CLIENT', 'AGENCY_ADMIN')")
    public ResponseEntity<ShiftResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateShiftRequest request,
                                                Authentication authentication) {
        return ResponseEntity.ok(shiftService.update(
                id, request, userService.getByEmail(authentication.getName())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACILITY', 'CLIENT', 'AGENCY_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        shiftService.delete(id, userService.getByEmail(authentication.getName()));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/platform-payment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShiftResponse> updatePlatformPayment(
            @PathVariable UUID id,
            @RequestBody UpdatePlatformPaymentRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(shiftService.updatePlatformPayment(
                id, request.platformPaid(), userService.getByEmail(authentication.getName())));
    }
}
