package com.okaynow.onboarding.controller;

import com.okaynow.onboarding.dto.CreateOnboardingRequest;
import com.okaynow.onboarding.dto.OnboardingRequestResponse;
import com.okaynow.onboarding.service.OnboardingService;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOnboardingController {

    private final OnboardingService onboardingService;
    private final UserService userService;

    @GetMapping("/users/{userId}/onboarding-requests")
    public ResponseEntity<List<OnboardingRequestResponse>> list(@PathVariable UUID userId) {
        return ResponseEntity.ok(onboardingService.listForUser(userId));
    }

    @PostMapping("/users/{userId}/onboarding-requests")
    public ResponseEntity<OnboardingRequestResponse> create(
            @PathVariable UUID userId,
            @Valid @RequestBody CreateOnboardingRequest body,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(onboardingService.createAdminRequest(
                        userId, body, userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/users/{userId}/approve-review")
    public ResponseEntity<Map<String, String>> approve(
            @PathVariable UUID userId,
            Authentication authentication) {
        onboardingService.approveReview(userId, userService.getByEmail(authentication.getName()));
        return ResponseEntity.ok(Map.of("message", "Account approved"));
    }

    @PostMapping("/onboarding-requests/{requestId}/cancel")
    public ResponseEntity<OnboardingRequestResponse> cancel(
            @PathVariable UUID requestId,
            Authentication authentication) {
        return ResponseEntity.ok(onboardingService.cancelRequest(
                requestId, userService.getByEmail(authentication.getName())));
    }
}
