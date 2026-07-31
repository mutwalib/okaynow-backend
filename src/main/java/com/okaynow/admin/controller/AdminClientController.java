package com.okaynow.admin.controller;

import com.okaynow.admin.dto.AdminClientResponse;
import com.okaynow.admin.dto.CreateClientRequest;
import com.okaynow.admin.dto.UpdateClientShiftPermissionsRequest;
import com.okaynow.admin.service.AdminClientService;
import com.okaynow.common.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/clients")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminClientController {

    private final AdminClientService adminClientService;

    @GetMapping
    public ResponseEntity<PagedResponse<AdminClientResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(adminClientService.search(
                search,
                PageRequest.of(page, Math.min(size, 100))));
    }

    @PostMapping
    public ResponseEntity<AdminClientResponse> create(
            @Valid @RequestBody CreateClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminClientService.create(request));
    }

    @PatchMapping("/{id}/shift-permissions")
    public ResponseEntity<AdminClientResponse> updateShiftPermissions(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientShiftPermissionsRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(adminClientService.updateShiftPermissions(
                id, request, authentication.getName()));
    }
}
