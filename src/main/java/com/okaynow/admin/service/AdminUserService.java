package com.okaynow.admin.service;

import com.okaynow.admin.dto.AdminUserResponse;
import com.okaynow.admin.dto.AdminUserReviewDetailResponse;
import com.okaynow.admin.dto.CreateAdminRequest;
import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.domain.AgencyStaff;
import com.okaynow.agencies.repository.AgencyStaffRepository;
import com.okaynow.audit.domain.AuditAction;
import com.okaynow.audit.service.AuditLogService;
import com.okaynow.common.dto.PagedResponse;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.marketplace.domain.CaregiverCredential;
import com.okaynow.marketplace.repository.CaregiverCredentialRepository;
import com.okaynow.onboarding.domain.OnboardingRequest;
import com.okaynow.onboarding.domain.OnboardingRequestStatus;
import com.okaynow.onboarding.repository.OnboardingRequestRepository;
import com.okaynow.onboarding.service.OnboardingService;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.FacilityProfile;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.FacilityProfileRepository;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OnboardingService onboardingService;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final OnboardingRequestRepository onboardingRequestRepository;
    private final CaregiverCredentialRepository caregiverCredentialRepository;
    private final AgencyStaffRepository agencyStaffRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PagedResponse<AdminUserResponse> search(
            Role role, UserStatus status, String search, UUID agencyId, Pageable pageable) {
        String normalizedSearch = search == null ? "" : search.trim();
        return PagedResponse.from(
                userRepository.search(role, status, normalizedSearch, agencyId, pageable)
                        .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public AdminUserReviewDetailResponse reviewDetail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        AdminUserReviewDetailResponse.CaregiverReviewProfile caregiver = null;
        AdminUserReviewDetailResponse.ClientReviewProfile client = null;
        AdminUserReviewDetailResponse.AgencyStaffReviewProfile agencyStaff = null;
        List<AdminUserReviewDetailResponse.CredentialSummary> credentials = List.of();

        if (user.getRole() == Role.CAREGIVER) {
            CaregiverProfile profile = caregiverProfileRepository.findByUserId(userId).orElse(null);
            if (profile != null) {
                caregiver = new AdminUserReviewDetailResponse.CaregiverReviewProfile(
                        profile.getId(),
                        profile.getFirstName(),
                        profile.getLastName(),
                        profile.getQualifications(),
                        profile.getOtherQualificationDetail(),
                        profile.getHourlyRateMin(),
                        profile.getHourlyRateMax(),
                        profile.getServiceRadiusMiles(),
                        profile.getHomeAddressLine(),
                        profile.getHomeCity(),
                        profile.getHomeState(),
                        profile.getHomeZip(),
                        profile.getHomeLat(),
                        profile.getHomeLng(),
                        profile.getProfilePhotoUrl());
                credentials = caregiverCredentialRepository
                        .findByCaregiverProfileIdOrderByCredentialTypeAsc(profile.getId())
                        .stream()
                        .map(this::toCredentialSummary)
                        .toList();
            }
        } else if (user.getRole() == Role.CLIENT) {
            ClientProfile profile = clientProfileRepository.findByUserId(userId).orElse(null);
            if (profile != null) {
                client = new AdminUserReviewDetailResponse.ClientReviewProfile(
                        profile.getId(),
                        profile.getFirstName(),
                        profile.getLastName(),
                        profile.getAddressLine(),
                        profile.getCity(),
                        profile.getState(),
                        profile.getZip(),
                        profile.getCareNeeds(),
                        profile.isRegisteringForSelf(),
                        profile.getMedicaidEligible(),
                        profile.getRelationshipToCareRecipient());
            }
        } else if (user.getRole() == Role.AGENCY_ADMIN) {
            agencyStaff = agencyStaffRepository.findFirstByUserIdWithAgency(userId)
                    .map(this::toAgencyStaffReviewProfile)
                    .orElse(null);
        }

        List<OnboardingRequest> requests =
                onboardingRequestRepository.findByUserIdOrderByCreatedAtAsc(userId);
        long open = requests.stream().filter(r -> r.getStatus() == OnboardingRequestStatus.OPEN).count();
        long submitted = requests.stream()
                .filter(r -> r.getStatus() == OnboardingRequestStatus.SUBMITTED)
                .count();

        return new AdminUserReviewDetailResponse(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.isEmailVerified(),
                user.getEmailVerifiedAt(),
                user.getCreatedAt(),
                displayName(user),
                user.getStatus() == UserStatus.PENDING_REVIEW,
                open,
                submitted,
                caregiver,
                client,
                agencyStaff,
                credentials,
                requests.stream().map(this::toKycSummary).toList());
    }

    @Transactional
    public AdminUserResponse updateStatus(
            UUID userId, UserStatus status, String actingAdminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getEmail().equalsIgnoreCase(actingAdminEmail)
                && status != UserStatus.ACTIVE) {
            throw new BadRequestException("Platform owners cannot change their own account status away from ACTIVE");
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
    public AdminUserReviewDetailResponse correctLegalName(
            UUID userId, String firstName, String lastName, String actingAdminEmail) {
        String nextFirst = normalizePersonName(firstName);
        String nextLast = normalizePersonName(lastName);
        if (nextFirst.isEmpty() || nextLast.isEmpty()) {
            throw new BadRequestException("First and last name are required");
        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User admin = userRepository.findByEmail(actingAdminEmail.toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));

        String previous;
        if (target.getRole() == Role.CAREGIVER) {
            CaregiverProfile profile = caregiverProfileRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
            previous = profile.getFirstName() + " " + profile.getLastName();
            profile.setFirstName(nextFirst);
            profile.setLastName(nextLast);
        } else if (target.getRole() == Role.CLIENT) {
            ClientProfile profile = clientProfileRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
            previous = profile.getFirstName() + " " + profile.getLastName();
            profile.setFirstName(nextFirst);
            profile.setLastName(nextLast);
        } else if (target.getRole() == Role.FACILITY) {
            FacilityProfile profile = facilityProfileRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));
            previous = profile.getContactFirstName() + " " + profile.getContactLastName();
            profile.setContactFirstName(nextFirst);
            profile.setContactLastName(nextLast);
        } else {
            throw new BadRequestException("Legal name corrections apply to caregivers, clients, and facilities only");
        }

        auditLogService.record(
                admin,
                AuditAction.LEGAL_NAME_CORRECTED_BY_ADMIN,
                "USER",
                target.getId(),
                null,
                "previous=" + previous.trim() + "; next=" + nextFirst + " " + nextLast);

        return reviewDetail(userId);
    }

    private static String normalizePersonName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
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
        AgencyStaff staff = null;
        if (user.getRole() == Role.AGENCY_ADMIN) {
            staff = agencyStaffRepository.findFirstByUserIdWithAgency(user.getId()).orElse(null);
        }
        Agency agency = staff == null ? null : staff.getAgency();
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.isEmailVerified(),
                displayName(user),
                agency == null ? null : agency.getId(),
                agency == null ? null : agency.getSlug(),
                agency == null ? null : agency.getDisplayName(),
                staff == null ? null : staff.getRole(),
                user.getCreatedAt());
    }

    private AdminUserReviewDetailResponse.AgencyStaffReviewProfile toAgencyStaffReviewProfile(
            AgencyStaff staff) {
        Agency agency = staff.getAgency();
        return new AdminUserReviewDetailResponse.AgencyStaffReviewProfile(
                agency.getId(),
                agency.getSlug(),
                agency.getDisplayName(),
                staff.getRole(),
                agency.getSubscriptionStatus(),
                agency.getSubscriptionPlan(),
                agency.isDirectoryListed(),
                agency.isHiringOpen());
    }

    private String displayName(User user) {
        if (user.getRole() == Role.AGENCY_ADMIN) {
            return agencyStaffRepository.findFirstByUserIdWithAgency(user.getId())
                    .map(staff -> staff.getAgency().getDisplayName() + " · " + user.getEmail())
                    .orElse(user.getEmail());
        }
        if (user.getRole() == Role.CAREGIVER) {
            return caregiverProfileRepository.findByUserId(user.getId())
                    .map(p -> (safe(p.getFirstName()) + " " + safe(p.getLastName())).trim())
                    .filter(s -> !s.isBlank())
                    .orElse(user.getEmail());
        }
        if (user.getRole() == Role.CLIENT) {
            return clientProfileRepository.findByUserId(user.getId())
                    .map(p -> (safe(p.getFirstName()) + " " + safe(p.getLastName())).trim())
                    .filter(s -> !s.isBlank())
                    .orElse(user.getEmail());
        }
        return user.getEmail();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private AdminUserReviewDetailResponse.CredentialSummary toCredentialSummary(CaregiverCredential c) {
        return new AdminUserReviewDetailResponse.CredentialSummary(
                c.getId(),
                c.getCredentialType().name(),
                c.getLicenseNumber(),
                c.getIssueDate(),
                c.getExpiryDate(),
                c.getDocumentUrl(),
                c.getVerificationStatus().name());
    }

    private AdminUserReviewDetailResponse.KycRequestSummary toKycSummary(OnboardingRequest req) {
        return new AdminUserReviewDetailResponse.KycRequestSummary(
                req.getId(),
                req.getTitle(),
                req.getInstructions(),
                req.getFieldType(),
                req.getStatus(),
                req.getResponseText(),
                req.getFileUrl(),
                req.getCreatedAt(),
                req.getSubmittedAt());
    }
}
