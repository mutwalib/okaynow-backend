package com.okaynow.admin.controller;

import com.okaynow.admin.dto.AdminUserResponse;
import com.okaynow.admin.dto.AdminUserReviewDetailResponse;
import com.okaynow.admin.dto.CreateAdminRequest;
import com.okaynow.admin.dto.UpdateUserStatusRequest;
import com.okaynow.admin.service.AdminUserService;
import com.okaynow.common.dto.PagedResponse;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.UserStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<PagedResponse<AdminUserResponse>> search(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(adminUserService.search(
                role,
                status,
                search,
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/{id}/review")
    public ResponseEntity<AdminUserReviewDetailResponse> reviewDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(adminUserService.reviewDetail(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AdminUserResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                adminUserService.updateStatus(id, request.status(), authentication.getName()));
    }

    @PostMapping("/owners")
    public ResponseEntity<AdminUserResponse> createOwner(
            @Valid @RequestBody CreateAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminUserService.createOwner(request));
    }
}
