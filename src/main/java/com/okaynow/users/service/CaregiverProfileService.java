package com.okaynow.users.service;

import com.okaynow.audit.domain.AuditAction;
import com.okaynow.audit.service.AuditLogService;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.common.geo.GeocodingService;
import com.okaynow.common.geo.ServiceRegionService;
import com.okaynow.notifications.domain.NotificationType;
import com.okaynow.notifications.service.NotificationService;
import com.okaynow.onboarding.domain.OnboardingFieldType;
import com.okaynow.onboarding.domain.OnboardingRequest;
import com.okaynow.onboarding.domain.OnboardingRequestStatus;
import com.okaynow.onboarding.repository.OnboardingRequestRepository;
import com.okaynow.storage.LocalFileStorageService;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.Qualification;
import com.okaynow.users.domain.User;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.dto.CaregiverProfileResponse;
import com.okaynow.users.dto.UpdateCaregiverProfileRequest;
import com.okaynow.users.mapper.UserMapper;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.repository.UserRepository;
import com.okaynow.users.support.LegalNameGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaregiverProfileService {

    private final CaregiverProfileRepository caregiverProfileRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final LocalFileStorageService fileStorageService;
    private final ServiceRegionService serviceRegionService;
    private final GeocodingService geocodingService;
    private final OnboardingRequestRepository onboardingRequestRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public CaregiverProfileResponse getByUserId(UUID userId) {
        return userMapper.toCaregiverProfileResponse(findByUserId(userId));
    }

    @Transactional
    public CaregiverProfileResponse update(UUID userId, UpdateCaregiverProfileRequest request) {
        assertProfileEditable(userId);
        CaregiverProfile profile = findByUserId(userId);
        LegalNameGuard.assertUnchanged(
                profile.getFirstName(), profile.getLastName(),
                request.firstName(), request.lastName());
        if (request.qualifications() != null) {
            // Defensive: JPA rows from older migrations may have null collection state.
            // Null here would otherwise cause an unhandled NPE and a generic 500.
            if (profile.getQualifications() == null) {
                profile.setQualifications(new LinkedHashSet<>());
            }
            profile.getQualifications().clear();
            profile.getQualifications().addAll(request.qualifications());
        }
        profile.setHourlyRateMin(request.hourlyRateMin());
        profile.setHourlyRateMax(request.hourlyRateMax());
        profile.setServiceRadiusMiles(request.serviceRadiusMiles());

        boolean addressProvided = !isBlank(request.homeAddressLine())
                || !isBlank(request.homeCity())
                || !isBlank(request.homeZip())
                || !isBlank(request.homeState());
        if (addressProvided) {
            if (isBlank(request.homeAddressLine()) || isBlank(request.homeCity()) || isBlank(request.homeZip())) {
                throw new BadRequestException("Enter street address, city, and ZIP for your home location");
            }
            var region = serviceRegionService.validate(request.homeState(), request.homeZip());
            profile.setHomeAddressLine(request.homeAddressLine().trim());
            profile.setHomeCity(request.homeCity().trim());
            profile.setHomeState(region.state());
            profile.setHomeZip(region.zip());
            var coords = geocodingService.requireGeocode(
                    profile.getHomeAddressLine(),
                    profile.getHomeCity(),
                    profile.getHomeState(),
                    profile.getHomeZip());
            profile.setHomeLat(coords.lat());
            profile.setHomeLng(coords.lng());
        } else if (request.homeLat() != null && request.homeLng() != null) {
            profile.setHomeLat(request.homeLat());
            profile.setHomeLng(request.homeLng());
        }

        return userMapper.toCaregiverProfileResponse(profile);
    }

    /**
     * Adds qualifications only (never removes). Triggers agency reverification when the
     * account was already submitted or active.
     */
    @Transactional
    public CaregiverProfileResponse addQualifications(UUID userId, Set<Qualification> toAdd) {
        if (toAdd == null || toAdd.isEmpty()) {
            throw new BadRequestException("Select at least one qualification to add");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        CaregiverProfile profile = findByUserId(userId);

        Set<Qualification> existing = new LinkedHashSet<>(
                profile.getQualifications() == null ? new LinkedHashSet<>() : profile.getQualifications());
        Set<Qualification> added = toAdd.stream()
                .filter(q -> !existing.contains(q))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (added.isEmpty()) {
            throw new BadRequestException("Those qualifications are already on your profile");
        }
        profile.getQualifications().addAll(added);

        boolean needsReverification = user.getStatus() == UserStatus.ACTIVE
                || user.getApplicationSubmittedAt() != null;
        if (needsReverification) {
            if (user.getStatus() == UserStatus.ACTIVE) {
                user.setStatus(UserStatus.PENDING_REVIEW);
            }
            user.setApplicationSubmittedAt(null);
            String labels = added.stream().map(CaregiverProfileService::labelFor).collect(Collectors.joining(", "));
            onboardingRequestRepository.save(OnboardingRequest.builder()
                    .userId(user.getId())
                    .requestedByUserId(null)
                    .title("Verify new qualification" + (added.size() == 1 ? "" : "s") + ": " + labels)
                    .instructions(
                            "You added new qualification(s) to your profile. Upload proof of certification/license "
                                    + "for: " + labels + ".")
                    .fieldType(OnboardingFieldType.FILE)
                    .status(OnboardingRequestStatus.OPEN)
                    .build());
            notificationService.notifyUser(
                    user,
                    NotificationType.ONBOARDING_INFO_REQUESTED,
                    "New qualification needs verification",
                    "Upload proof for: " + labels + ". Your account is under agency review until approved.",
                    null);
            auditLogService.record(user, AuditAction.CAREGIVER_QUALIFICATIONS_ADDED, "USER", user.getId(), null,
                    "added=" + labels);
        }

        return userMapper.toCaregiverProfileResponse(profile);
    }

    @Transactional
    public CaregiverProfileResponse uploadPhoto(UUID userId, MultipartFile file) {
        assertProfileEditable(userId);
        CaregiverProfile profile = findByUserId(userId);
        profile.setProfilePhotoUrl(fileStorageService.storeProfilePhoto(profile.getId(), file));
        return userMapper.toCaregiverProfileResponse(profile);
    }

    private void assertProfileEditable(UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BadRequestException(
                    "Your profile is locked after verification. You can add a new qualification (which requires agency review) or change your password.");
        }
        if (user.getApplicationSubmittedAt() != null) {
            throw new BadRequestException(
                    "Your application is locked after submission. You can add a new qualification (which reopens review) if needed.");
        }
    }

    private CaregiverProfile findByUserId(UUID userId) {
        return caregiverProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String labelFor(Qualification q) {
        return switch (q) {
            case MAP -> "MAP certification";
            case OTHER -> "Other (not specified)";
            default -> q.name();
        };
    }
}
