package com.okaynow.onboarding.controller;

import com.okaynow.onboarding.dto.OnboardingRequestResponse;
import com.okaynow.onboarding.dto.OnboardingStatusResponse;
import com.okaynow.onboarding.dto.SubmitOnboardingTextRequest;
import com.okaynow.onboarding.service.OnboardingService;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<OnboardingStatusResponse> me(Authentication authentication) {
        return ResponseEntity.ok(onboardingService.statusFor(
                userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/me/submit")
    public ResponseEntity<OnboardingStatusResponse> submitApplication(Authentication authentication) {
        return ResponseEntity.ok(onboardingService.submitApplication(
                userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/me/requests/{requestId}/text")
    public ResponseEntity<OnboardingRequestResponse> submitText(
            @PathVariable UUID requestId,
            @Valid @RequestBody SubmitOnboardingTextRequest body,
            Authentication authentication) {
        return ResponseEntity.ok(onboardingService.submitText(
                userService.getByEmail(authentication.getName()),
                requestId,
                body.responseText()));
    }

    @PostMapping(value = "/me/requests/{requestId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OnboardingRequestResponse> submitFile(
            @PathVariable UUID requestId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        return ResponseEntity.ok(onboardingService.submitFile(
                userService.getByEmail(authentication.getName()),
                requestId,
                file));
    }
}
