package com.okaynow.booking.controller;

import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.booking.dto.AssignCaregiverRequest;
import com.okaynow.booking.dto.CancelClaimRequest;
import com.okaynow.booking.dto.InviteCaregiverRequest;
import com.okaynow.booking.dto.ShiftClaimResponse;
import com.okaynow.booking.dto.ExtendShiftRequest;
import com.okaynow.booking.service.BookingService;
import com.okaynow.common.dto.PagedResponse;
import com.okaynow.shifts.dto.ShiftResponse;
import com.okaynow.shifts.service.ShiftService;
import com.okaynow.users.service.UserService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

/**
 * Agency back-office booking oversight: claim list/confirm/cancel and explicit
 * shift lifecycle transitions (publish/start/complete).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingController {

    private final BookingService bookingService;
    private final ShiftService shiftService;
    private final UserService userService;

    @GetMapping("/claims")
    public ResponseEntity<PagedResponse<ShiftClaimResponse>> allClaims(
            @RequestParam(required = false) ShiftClaimStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(bookingService.allClaims(status,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "claimedAt"))));
    }

    @GetMapping("/shifts/{id}/claims")
    public ResponseEntity<List<ShiftClaimResponse>> claimsForShift(@PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.claimsForShift(id));
    }

    @PostMapping("/claims/{claimId}/confirm")
    public ResponseEntity<ShiftClaimResponse> confirm(@PathVariable UUID claimId) {
        return ResponseEntity.ok(bookingService.confirm(claimId));
    }

    @PostMapping("/claims/{claimId}/cancel")
    public ResponseEntity<ShiftClaimResponse> cancel(
            @PathVariable UUID claimId,
            @Valid @RequestBody CancelClaimRequest request) {
        return ResponseEntity.ok(bookingService.cancel(claimId, request.cancelReason()));
    }

    @PostMapping("/shifts/{id}/publish")
    public ResponseEntity<ShiftResponse> publish(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(shiftService.publish(
                id, userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/shifts/{id}/unpublish")
    public ResponseEntity<ShiftResponse> unpublish(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(shiftService.unpublish(
                id, userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/shifts/{id}/invite")
    public ResponseEntity<ShiftClaimResponse> invite(
            @PathVariable UUID id,
            @Valid @RequestBody InviteCaregiverRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.invite(
                id,
                request.caregiverProfileId(),
                userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/shifts/{id}/assign")
    public ResponseEntity<ShiftClaimResponse> assign(
            @PathVariable UUID id,
            @Valid @RequestBody AssignCaregiverRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(bookingService.assign(
                id, request.caregiverProfileId(), authentication.getName()));
    }

    @PostMapping("/shifts/{id}/unassign")
    public ResponseEntity<ShiftClaimResponse> unassign(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(bookingService.unassign(id, authentication.getName()));
    }

    @PostMapping("/shifts/{id}/start")
    public ResponseEntity<Void> startShift(@PathVariable UUID id, Authentication authentication) {
        bookingService.startShift(id, userService.getByEmail(authentication.getName()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/shifts/{id}/revert-start")
    public ResponseEntity<Void> revertStart(@PathVariable UUID id, Authentication authentication) {
        bookingService.revertStart(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/shifts/{id}/cancel")
    public ResponseEntity<Void> cancelShift(@PathVariable UUID id) {
        bookingService.cancelShift(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/shifts/{id}/reopen")
    public ResponseEntity<Void> reopenCancelled(
            @PathVariable UUID id, Authentication authentication) {
        bookingService.reopenCancelled(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/shifts/{id}/complete")
    public ResponseEntity<Void> completeShift(@PathVariable UUID id) {
        bookingService.completeShift(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/shifts/{id}/revert-complete")
    public ResponseEntity<Void> revertComplete(
            @PathVariable UUID id, Authentication authentication) {
        bookingService.revertComplete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/shifts/{id}/extend")
    public ResponseEntity<Void> extendShift(
            @PathVariable UUID id,
            @Valid @RequestBody ExtendShiftRequest request,
            Authentication authentication) {
        bookingService.extendShift(id, request.endTime(), authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
