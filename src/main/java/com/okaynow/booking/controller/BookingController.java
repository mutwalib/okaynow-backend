package com.okaynow.booking.controller;

import com.okaynow.booking.dto.ShiftClaimResponse;
import com.okaynow.booking.service.BookingService;
import com.okaynow.common.dto.PagedResponse;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Caregiver-facing booking endpoints: claim/release a shift, list own claims.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/shifts/{id}/claim")
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<ShiftClaimResponse> claim(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.claim(id, authentication.getName()));
    }

    @PostMapping("/shifts/{id}/release")
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<ShiftClaimResponse> release(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(bookingService.release(id, authentication.getName()));
    }

    @GetMapping("/claims/me")
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<PagedResponse<ShiftClaimResponse>> myClaims(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(bookingService.myClaims(authentication.getName(),
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "claimedAt"))));
    }
}
