package com.okaynow.users.controller;

import com.okaynow.users.dto.FacilityProfileResponse;
import com.okaynow.users.dto.UpdateFacilityProfileRequest;
import com.okaynow.users.service.FacilityProfileService;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/facilities")
@RequiredArgsConstructor
public class FacilityController {

    private final FacilityProfileService facilityProfileService;
    private final UserService userService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('FACILITY')")
    public ResponseEntity<FacilityProfileResponse> me(Authentication authentication) {
        return ResponseEntity.ok(facilityProfileService.getByUserId(currentUserId(authentication)));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('FACILITY')")
    public ResponseEntity<FacilityProfileResponse> update(
            Authentication authentication,
            @Valid @RequestBody UpdateFacilityProfileRequest request) {
        return ResponseEntity.ok(
                facilityProfileService.update(currentUserId(authentication), request));
    }

    private UUID currentUserId(Authentication authentication) {
        return userService.getByEmail(authentication.getName()).getId();
    }
}
