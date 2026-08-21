package com.okaynow.users.controller;

import com.okaynow.users.dto.AddCaregiverQualificationsRequest;
import com.okaynow.users.dto.CaregiverProfileResponse;
import com.okaynow.users.dto.UpdateCaregiverProfileRequest;
import com.okaynow.users.service.CaregiverProfileService;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/caregivers")
@RequiredArgsConstructor
public class CaregiverController {

    private final CaregiverProfileService caregiverProfileService;
    private final UserService userService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<CaregiverProfileResponse> me(Authentication authentication) {
        return ResponseEntity.ok(caregiverProfileService.getByUserId(currentUserId(authentication)));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<CaregiverProfileResponse> update(Authentication authentication,
                                                           @Valid @RequestBody UpdateCaregiverProfileRequest request) {
        return ResponseEntity.ok(caregiverProfileService.update(currentUserId(authentication), request));
    }

    @PostMapping("/me/qualifications")
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<CaregiverProfileResponse> addQualifications(
            Authentication authentication,
            @Valid @RequestBody AddCaregiverQualificationsRequest request) {
        return ResponseEntity.ok(caregiverProfileService.addQualifications(
                currentUserId(authentication),
                request.qualifications(),
                request.otherQualificationDetail()));
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<CaregiverProfileResponse> uploadPhoto(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(
                caregiverProfileService.uploadPhoto(currentUserId(authentication), file));
    }

    private UUID currentUserId(Authentication authentication) {
        return userService.getByEmail(authentication.getName()).getId();
    }
}
