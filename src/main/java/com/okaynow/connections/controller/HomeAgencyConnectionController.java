package com.okaynow.connections.controller;

import com.okaynow.connections.dto.ConnectAgencyRequest;
import com.okaynow.connections.dto.HomeAgencyConnectionResponse;
import com.okaynow.connections.service.HomeAgencyConnectionService;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/home/agencies")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CLIENT', 'FACILITY')")
public class HomeAgencyConnectionController {

    private final HomeAgencyConnectionService connectionService;
    private final UserService userService;

    @PostMapping("/{agencyId}/connect-request")
    @ResponseStatus(HttpStatus.CREATED)
    public HomeAgencyConnectionResponse connectRequest(
            Authentication authentication,
            @PathVariable UUID agencyId,
            @RequestBody(required = false) @Valid ConnectAgencyRequest request) {
        return connectionService.requestConnection(
                currentUserId(authentication), agencyId, request);
    }

    @GetMapping("/connected")
    public ResponseEntity<List<HomeAgencyConnectionResponse>> connected(Authentication authentication) {
        return ResponseEntity.ok(connectionService.listForHome(currentUserId(authentication)));
    }

    @DeleteMapping("/{agencyId}/connection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void endConnection(Authentication authentication, @PathVariable UUID agencyId) {
        connectionService.endConnectionForHome(currentUserId(authentication), agencyId);
    }

    private UUID currentUserId(Authentication authentication) {
        return userService.getByEmail(authentication.getName()).getId();
    }
}
