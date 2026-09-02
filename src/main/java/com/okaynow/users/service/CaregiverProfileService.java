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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
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

    /**
     * Updates caregiver application details. After submission or while ACTIVE, any material
     * change moves the account back to {@link UserStatus#PENDING_REVIEW}.
     */
    @Transactional
    public CaregiverProfileResponse update(UUID userId, UpdateCaregiverProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        CaregiverProfile profile = findByUserId(userId);

        // Legal names are not self-service. Agency staff must correct them on request.
        LegalNameGuard.assertUnchanged(
                profile.getFirstName(), profile.getLastName(),
                request.firstName(), request.lastName());

        Set<Qualification> nextQuals = request.qualifications() == null
                ? null
                : new LinkedHashSet<>(request.qualifications());
        if (nextQuals != null && nextQuals.isEmpty()) {
            throw new BadRequestException("Select at least one qualification");
        }
        Set<Qualification> effectiveQuals = nextQuals != null ? nextQuals : currentQualsOrEmpty(profile);
        String nextOtherDetail = resolveOtherQualificationDetail(effectiveQuals, request.otherQualificationDetail());
        Set<Qualification> currentQuals = new LinkedHashSet<>(
                profile.getQualifications() == null ? Set.of() : profile.getQualifications());
        boolean qualsChanged = nextQuals != null && !nextQuals.equals(currentQuals);
        boolean otherDetailChanged = !normalizeText(profile.getOtherQualificationDetail())
                .equals(normalizeText(nextOtherDetail));

        boolean addressProvided = !isBlank(request.homeAddressLine())
                || !isBlank(request.homeCity())
                || !isBlank(request.homeZip())
                || !isBlank(request.homeState());
        boolean addressChanged = false;
        if (addressProvided) {
            if (isBlank(request.homeAddressLine()) || isBlank(request.homeCity()) || isBlank(request.homeZip())) {
                throw new BadRequestException("Enter street address, city, and ZIP for your home location");
            }
            addressChanged = !normalizeText(profile.getHomeAddressLine()).equals(normalizeText(request.homeAddressLine()))
                    || !normalizeText(profile.getHomeCity()).equals(normalizeText(request.homeCity()))
                    || !normalizeText(profile.getHomeZip()).equals(normalizeText(request.homeZip()))
                    || (!isBlank(request.homeState())
                    && !normalizeText(profile.getHomeState()).equals(normalizeText(request.homeState())));
        }

        boolean latLngChanged = request.homeLat() != null && request.homeLng() != null
                && (!Objects.equals(profile.getHomeLat(), request.homeLat())
                || !Objects.equals(profile.getHomeLng(), request.homeLng()));

        // Pay range and service radius can change freely. Reverification is only for
        // qualifications / home location (identity and matching-critical fields).
        boolean requiresReverification = qualsChanged || otherDetailChanged || addressChanged || latLngChanged;

        if (nextQuals != null) {
            // Defensive: JPA rows from older migrations may have null collection state.
            if (profile.getQualifications() == null) {
                profile.setQualifications(new LinkedHashSet<>());
            }
            profile.getQualifications().clear();
            profile.getQualifications().addAll(nextQuals);
        }
        profile.setOtherQualificationDetail(nextOtherDetail);
        profile.setHourlyRateMin(request.hourlyRateMin());
        profile.setHourlyRateMax(request.hourlyRateMax());
        profile.setServiceRadiusMiles(request.serviceRadiusMiles());

        if (addressProvided) {
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

        boolean needsReverification = requiresReverification
                && (user.getStatus() == UserStatus.ACTIVE || user.getApplicationSubmittedAt() != null);
        if (needsReverification) {
            triggerProfileReverification(user, nextQuals, currentQuals, qualsChanged);
        }

        return userMapper.toCaregiverProfileResponse(profile);
    }

    /**
     * Adds qualifications only (never removes). Triggers agency reverification when the
     * account was already submitted or active.
     */
    @Transactional
    public CaregiverProfileResponse addQualifications(
            UUID userId, Set<Qualification> toAdd, String otherQualificationDetail) {
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
        Set<Qualification> merged = new LinkedHashSet<>(existing);
        merged.addAll(added);
        String nextOtherDetail = resolveOtherQualificationDetail(
                merged,
                otherQualificationDetail != null ? otherQualificationDetail : profile.getOtherQualificationDetail());
        profile.getQualifications().addAll(added);
        profile.setOtherQualificationDetail(nextOtherDetail);

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
        assertPhotoEditable(userId);
        CaregiverProfile profile = findByUserId(userId);
        profile.setProfilePhotoUrl(fileStorageService.storeProfilePhoto(profile.getId(), file));
        return userMapper.toCaregiverProfileResponse(profile);
    }

    @Transactional
    public CaregiverProfileResponse uploadCv(UUID userId, MultipartFile file) {
        CaregiverProfile profile = findByUserId(userId);
        profile.setCvUrl(fileStorageService.storeCaregiverCv(profile.getId(), file));
        profile.setCvUploadedAt(Instant.now());
        return userMapper.toCaregiverProfileResponse(profile);
    }

    private void triggerProfileReverification(
            User user,
            Set<Qualification> nextQuals,
            Set<Qualification> previousQuals,
            boolean qualsChanged) {
        if (user.getStatus() == UserStatus.ACTIVE) {
            user.setStatus(UserStatus.PENDING_REVIEW);
        }
        // Keep them in the under-review waiting room with the updated details.
        user.setApplicationSubmittedAt(Instant.now());

        if (qualsChanged && nextQuals != null) {
            Set<Qualification> newlyAdded = nextQuals.stream()
                    .filter(q -> !previousQuals.contains(q))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!newlyAdded.isEmpty()) {
                String labels = newlyAdded.stream()
                        .map(CaregiverProfileService::labelFor)
                        .collect(Collectors.joining(", "));
                onboardingRequestRepository.save(OnboardingRequest.builder()
                        .userId(user.getId())
                        .requestedByUserId(null)
                        .title("Verify new qualification" + (newlyAdded.size() == 1 ? "" : "s") + ": " + labels)
                        .instructions(
                                "You updated your qualifications. Upload proof of certification/license for: "
                                        + labels + ".")
                        .fieldType(OnboardingFieldType.FILE)
                        .status(OnboardingRequestStatus.OPEN)
                        .build());
            }
        }

        notificationService.notifyUser(
                user,
                NotificationType.ONBOARDING_INFO_REQUESTED,
                "Profile update under review",
                "You updated your application details. Your account is under agency review until approved again.",
                null);
        auditLogService.record(
                user,
                AuditAction.CAREGIVER_PROFILE_UPDATED_FOR_REVIEW,
                "USER",
                user.getId(),
                null,
                "status=PENDING_REVIEW");
    }

    private void assertPhotoEditable(UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BadRequestException(
                    "Your profile photo is locked after verification. You can update name, qualifications, rates, or address — that sends your account back for review.");
        }
        if (user.getApplicationSubmittedAt() != null) {
            throw new BadRequestException(
                    "Your profile photo is locked after submission. You can update other profile details, which reopens review.");
        }
    }

    private CaregiverProfile findByUserId(UUID userId) {
        return caregiverProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean sameDecimal(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.compareTo(b) == 0;
    }

    private static String labelFor(Qualification q) {
        return switch (q) {
            case MAP -> "MAP certification";
            case OTHER -> "Other (not specified)";
            default -> q.name();
        };
    }

    private static Set<Qualification> currentQualsOrEmpty(CaregiverProfile profile) {
        return new LinkedHashSet<>(
                profile.getQualifications() == null ? Set.of() : profile.getQualifications());
    }

    /**
     * When OTHER is selected, a free-text description is required. Otherwise clear it.
     */
    private static String resolveOtherQualificationDetail(
            Set<Qualification> qualifications, String requestedDetail) {
        boolean includesOther = qualifications != null && qualifications.contains(Qualification.OTHER);
        if (!includesOther) {
            return null;
        }
        String detail = requestedDetail == null ? "" : requestedDetail.trim().replaceAll("\\s+", " ");
        if (detail.isEmpty()) {
            throw new BadRequestException(
                    "Specify what your Other qualification is (required when Other is selected)");
        }
        if (detail.length() > 200) {
            throw new BadRequestException("Other qualification description must be 200 characters or fewer");
        }
        return detail;
    }
}
