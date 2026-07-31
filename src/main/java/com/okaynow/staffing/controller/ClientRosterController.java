package com.okaynow.staffing.controller;

import com.okaynow.staffing.dto.ClientRosterCaregiverResponse;
import com.okaynow.staffing.service.ClientStaffingService;
import com.okaynow.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clients/me/caregivers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENT')")
public class ClientRosterController {

    private final ClientStaffingService clientStaffingService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<ClientRosterCaregiverResponse>> myRoster(Authentication authentication) {
        return ResponseEntity.ok(clientStaffingService.listMineForClientUser(
                userService.getByEmail(authentication.getName())));
    }
}
