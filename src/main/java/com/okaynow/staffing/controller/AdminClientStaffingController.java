package com.okaynow.staffing.controller;

import com.okaynow.booking.service.BookingService;
import com.okaynow.staffing.dto.ClientCaregiverAssignmentResponse;
import com.okaynow.staffing.dto.ClientRosterChangeResponse;
import com.okaynow.staffing.dto.CreateClientCaregiverAssignmentRequest;
import com.okaynow.staffing.service.ClientStaffingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/clients/{clientId}/caregivers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminClientStaffingController {

    private final ClientStaffingService clientStaffingService;
    private final BookingService bookingService;

    @GetMapping
    public ResponseEntity<List<ClientCaregiverAssignmentResponse>> list(@PathVariable UUID clientId) {
        return ResponseEntity.ok(clientStaffingService.listForClient(clientId));
    }

    @PostMapping
    public ResponseEntity<ClientRosterChangeResponse> assign(
            @PathVariable UUID clientId,
            @Valid @RequestBody CreateClientCaregiverAssignmentRequest request,
            Authentication authentication) {
        ClientCaregiverAssignmentResponse assignment =
                clientStaffingService.assign(clientId, request, authentication.getName());
        int filled = 0;
        if (Boolean.TRUE.equals(request.fillOpenShifts())) {
            filled = bookingService.fillOpenClientShifts(
                    clientId, request.caregiverProfileId(), authentication.getName());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new ClientRosterChangeResponse(assignment, filled, 0));
    }

    /**
     * Assign this roster caregiver onto existing open client shifts (no new shifts).
     */
    @PostMapping("/{assignmentId}/fill-open-shifts")
    public ResponseEntity<ClientRosterChangeResponse> fillOpenShifts(
            @PathVariable UUID clientId,
            @PathVariable UUID assignmentId,
            Authentication authentication) {
        ClientCaregiverAssignmentResponse row = clientStaffingService.listForClient(clientId).stream()
                .filter(a -> a.id().equals(assignmentId))
                .findFirst()
                .orElseThrow(() -> new com.okaynow.common.exception.ResourceNotFoundException(
                        "Roster assignment not found"));
        int filled = bookingService.fillOpenClientShifts(
                clientId, row.caregiverProfileId(), authentication.getName());
        return ResponseEntity.ok(new ClientRosterChangeResponse(row, filled, 0));
    }

    /**
     * Remove from roster. By default also clears their upcoming schedule assignments
     * for this client ({@code clearSchedule=true}).
     */
    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<ClientRosterChangeResponse> unassign(
            @PathVariable UUID clientId,
            @PathVariable UUID assignmentId,
            @RequestParam(defaultValue = "true") boolean clearSchedule,
            Authentication authentication) {
        ClientCaregiverAssignmentResponse removed =
                clientStaffingService.unassign(clientId, assignmentId, authentication.getName());
        int released = 0;
        if (clearSchedule) {
            released = bookingService.releaseCaregiverFromClientSchedule(
                    clientId, removed.caregiverProfileId(), authentication.getName());
        }
        return ResponseEntity.ok(new ClientRosterChangeResponse(removed, 0, released));
    }
}
