package com.okaynow.reviews.controller;

import com.okaynow.common.dto.PagedResponse;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.reviews.domain.ReviewStatus;
import com.okaynow.reviews.dto.CreateReviewRequest;
import com.okaynow.reviews.dto.ModerateReviewRequest;
import com.okaynow.reviews.dto.ReviewResponse;
import com.okaynow.reviews.service.ReviewService;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final UserService userService;
    private final CaregiverProfileRepository caregiverProfileRepository;

    @PostMapping("/reviews")
    @PreAuthorize("hasAnyRole('CLIENT', 'FACILITY')")
    public ResponseEntity<ReviewResponse> create(
            @Valid @RequestBody CreateReviewRequest request,
            Authentication authentication) {
        User actor = userService.getByEmail(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(request, actor));
    }

    @GetMapping("/reviews/me")
    @PreAuthorize("hasAnyRole('CLIENT', 'FACILITY')")
    public ResponseEntity<List<ReviewResponse>> myReviews(Authentication authentication) {
        User actor = userService.getByEmail(authentication.getName());
        return ResponseEntity.ok(reviewService.myReviews(actor));
    }

    @GetMapping("/reviews/shift/{shiftId}")
    @PreAuthorize("hasAnyRole('CLIENT', 'FACILITY', 'CAREGIVER', 'ADMIN')")
    public ResponseEntity<ReviewResponse> forShift(
            @PathVariable UUID shiftId, Authentication authentication) {
        User actor = userService.getByEmail(authentication.getName());
        return ResponseEntity.ok(reviewService.forShift(shiftId, actor));
    }

    @GetMapping("/caregivers/{caregiverProfileId}/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ReviewResponse>> publishedForCaregiver(
            @PathVariable UUID caregiverProfileId) {
        return ResponseEntity.ok(reviewService.publishedForCaregiver(caregiverProfileId));
    }

    @GetMapping("/caregivers/me/reviews")
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<List<ReviewResponse>> myPublishedReviews(Authentication authentication) {
        User actor = userService.getByEmail(authentication.getName());
        UUID caregiverId = caregiverProfileRepository.findByUserId(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"))
                .getId();
        return ResponseEntity.ok(reviewService.publishedForCaregiver(caregiverId));
    }

    @GetMapping("/admin/reviews")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedResponse<ReviewResponse>> adminList(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(reviewService.adminList(
                status, PageRequest.of(page, Math.min(size, 100))));
    }

    @PatchMapping("/admin/reviews/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReviewResponse> moderate(
            @PathVariable UUID id,
            @Valid @RequestBody ModerateReviewRequest request,
            Authentication authentication) {
        User admin = userService.getByEmail(authentication.getName());
        return ResponseEntity.ok(reviewService.moderate(id, request, admin));
    }
}
