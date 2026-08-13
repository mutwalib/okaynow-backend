package com.okaynow.admin.service;

import com.okaynow.admin.dto.AdminUserResponse;
import com.okaynow.admin.dto.CreateAdminRequest;
import com.okaynow.common.dto.PagedResponse;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.onboarding.service.OnboardingService;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OnboardingService onboardingService;

    @Transactional(readOnly = true)
    public PagedResponse<AdminUserResponse> search(
            Role role, UserStatus status, String search, Pageable pageable) {
        String normalizedSearch = search == null ? "" : search.trim();
        return PagedResponse.from(
                userRepository.search(role, status, normalizedSearch, pageable)
                        .map(this::toResponse));
    }

    @Transactional
    public AdminUserResponse updateStatus(
            UUID userId, UserStatus status, String actingAdminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getEmail().equalsIgnoreCase(actingAdminEmail)
                && status != UserStatus.ACTIVE) {
            throw new BadRequestException("Platform owners cannot suspend or deactivate themselves");
        }
        if (status == UserStatus.ACTIVE
                && (user.getRole() == Role.CAREGIVER || user.getRole() == Role.CLIENT)
                && user.getStatus() == UserStatus.PENDING_REVIEW) {
            User admin = userRepository.findByEmail(actingAdminEmail.toLowerCase().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));
            onboardingService.approveReview(userId, admin);
            return toResponse(userRepository.findById(userId).orElseThrow());
        }
        user.setStatus(status);
        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse createOwner(CreateAdminRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("An account with this email already exists");
        }
        User user = userRepository.save(User.builder()
                .email(email)
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .emailVerifiedAt(java.time.Instant.now())
                .build());
        return toResponse(user);
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt());
    }
}
