package com.okaynow.shiftrequests.controller;

import com.okaynow.shiftrequests.dto.CreateShiftRequestPayload;
import com.okaynow.shiftrequests.dto.ShiftRequestResponse;
import com.okaynow.shiftrequests.service.ShiftRequestService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/home/shift-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENT')")
public class HomeShiftRequestController {

    private final ShiftRequestService shiftRequestService;
    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftRequestResponse create(
            Authentication authentication,
            @Valid @RequestBody CreateShiftRequestPayload payload) {
        return shiftRequestService.createForHome(currentUserId(authentication), payload);
    }

    @GetMapping
    public ResponseEntity<List<ShiftRequestResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(shiftRequestService.listForHome(currentUserId(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShiftRequestResponse> get(
            Authentication authentication,
            @PathVariable UUID id) {
        return ResponseEntity.ok(shiftRequestService.getForHome(currentUserId(authentication), id));
    }

    private UUID currentUserId(Authentication authentication) {
        return userService.getByEmail(authentication.getName()).getId();
    }
}
