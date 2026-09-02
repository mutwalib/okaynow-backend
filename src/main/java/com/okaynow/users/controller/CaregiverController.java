package com.okaynow.users.controller;

import com.okaynow.hiring.dto.CaregiverAgencyInterestResponse;
import com.okaynow.hiring.dto.ExpressInterestRequest;
import com.okaynow.hiring.service.CaregiverAgencyInterestService;
import com.okaynow.roster.dto.AgencyRosterEntryResponse;
import com.okaynow.roster.service.AgencyRosterService;
import com.okaynow.users.dto.AddCaregiverQualificationsRequest;
import com.okaynow.users.dto.CaregiverProfileResponse;
import com.okaynow.users.dto.UpdateCaregiverProfileRequest;
import com.okaynow.users.service.CaregiverProfileService;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/caregivers")
@RequiredArgsConstructor
public class CaregiverController {

    private final CaregiverProfileService caregiverProfileService;
    private final AgencyRosterService agencyRosterService;
    private final CaregiverAgencyInterestService interestService;
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

    @PostMapping(value = "/me/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<CaregiverProfileResponse> uploadCv(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(
                caregiverProfileService.uploadCv(currentUserId(authentication), file));
    }

    @GetMapping("/me/roster-invites")
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<List<AgencyRosterEntryResponse>> rosterInvites(Authentication authentication) {
        return ResponseEntity.ok(agencyRosterService.listInvitesForCaregiver(currentUserId(authentication)));
    }

    @GetMapping("/me/rosters")
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<List<AgencyRosterEntryResponse>> myRosters(Authentication authentication) {
        return ResponseEntity.ok(agencyRosterService.listMembershipsForCaregiver(currentUserId(authentication)));
    }

    @PostMapping("/me/roster-invites/{id}/accept")
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<AgencyRosterEntryResponse> acceptRosterInvite(
            Authentication authentication,
            @PathVariable UUID id) {
        return ResponseEntity.ok(agencyRosterService.acceptInvite(currentUserId(authentication), id));
    }

    @GetMapping("/me/agency-interests")
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<List<CaregiverAgencyInterestResponse>> myInterests(Authentication authentication) {
        return ResponseEntity.ok(interestService.listForCaregiver(currentUserId(authentication)));
    }

    @PostMapping("/me/agency-interests")
    @PreAuthorize("hasRole('CAREGIVER')")
    @ResponseStatus(HttpStatus.CREATED)
    public CaregiverAgencyInterestResponse expressInterest(
            Authentication authentication,
            @Valid @RequestBody ExpressInterestRequest request) {
        return interestService.expressInterest(currentUserId(authentication), request);
    }

    private UUID currentUserId(Authentication authentication) {
        return userService.getByEmail(authentication.getName()).getId();
    }
}
