package com.okaynow.onboarding.service;

import com.okaynow.audit.domain.AuditAction;
import com.okaynow.audit.service.AuditLogService;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.notifications.domain.NotificationType;
import com.okaynow.notifications.service.NotificationService;
import com.okaynow.onboarding.domain.OnboardingFieldType;
import com.okaynow.onboarding.domain.OnboardingRequest;
import com.okaynow.onboarding.domain.OnboardingRequestStatus;
import com.okaynow.onboarding.dto.CreateOnboardingRequest;
import com.okaynow.onboarding.dto.OnboardingRequestResponse;
import com.okaynow.onboarding.dto.OnboardingStatusResponse;
import com.okaynow.onboarding.repository.OnboardingRequestRepository;
import com.okaynow.storage.LocalFileStorageService;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.Qualification;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final EnumSet<OnboardingRequestStatus> OPEN_OR_SUBMITTED =
            EnumSet.of(OnboardingRequestStatus.OPEN, OnboardingRequestStatus.SUBMITTED);

    private final OnboardingRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final LocalFileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Transactional
    public void ensureSystemRequestsAfterEmailVerify(User user) {
        if (user.getRole() != Role.CAREGIVER) {
            return;
        }
        boolean hasPhotoRequest = requestRepository.existsByUserIdAndFieldTypeAndStatusIn(
                user.getId(),
                OnboardingFieldType.PROFILE_PHOTO,
                OPEN_OR_SUBMITTED);
        if (hasPhotoRequest) {
            return;
        }
        CaregiverProfile profile = caregiverProfileRepository.findByUserId(user.getId()).orElse(null);
        if (profile != null && profile.getProfilePhotoUrl() != null && !profile.getProfilePhotoUrl().isBlank()) {
            return;
        }
        requestRepository.save(OnboardingRequest.builder()
                .userId(user.getId())
                .requestedByUserId(null)
                .title("Profile photo")
                .instructions("Upload a clear photo of yourself for your caregiver profile.")
                .fieldType(OnboardingFieldType.PROFILE_PHOTO)
                .status(OnboardingRequestStatus.OPEN)
                .build());
    }

    @Transactional(readOnly = true)
    public OnboardingStatusResponse statusFor(User user) {
        boolean pending = user.getStatus() == UserStatus.PENDING_REVIEW;
        List<OnboardingRequestResponse> requests = requestRepository
                .findByUserIdOrderByCreatedAtAsc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
        List<String> missing = applicationMissing(user, requests);
        boolean applicationReady = missing.isEmpty();
        boolean applicationSubmitted = user.getApplicationSubmittedAt() != null;
        boolean applicationComplete = applicationSubmitted;
        String message;
        if (!pending) {
            message = "Your account is active.";
        } else if (applicationSubmitted) {
            message = "Thanks for registering on OkayNow. Our team is reviewing your application and will notify you once you are verified. If we need anything else, it will appear below.";
        } else if (applicationReady) {
            message = "Everything looks ready. Confirm and submit your application so our team can begin review.";
        } else {
            message = "Finish the steps below to complete your OkayNow application. Once everything is entered, you can submit it for review.";
        }
        return new OnboardingStatusResponse(
                user.getStatus(),
                pending,
                applicationReady,
                applicationSubmitted,
                applicationComplete,
                missing,
                message,
                requests);
    }

    @Transactional
    public OnboardingStatusResponse submitApplication(User actor) {
        if (actor.getStatus() != UserStatus.PENDING_REVIEW) {
            throw new BadRequestException("Only accounts pending review can submit an application");
        }
        List<OnboardingRequestResponse> requests = requestRepository
                .findByUserIdOrderByCreatedAtAsc(actor.getId())
                .stream()
                .map(this::toResponse)
                .toList();
        List<String> missing = applicationMissing(actor, requests);
        if (!missing.isEmpty()) {
            throw new BadRequestException(
                    "Finish these items before submitting: " + String.join("; ", missing));
        }
        actor.setApplicationSubmittedAt(Instant.now());
        userRepository.save(actor);
        auditLogService.record(actor, AuditAction.APPLICATION_SUBMITTED, "USER", actor.getId(), null, null);
        return statusFor(actor);
    }

    private List<String> applicationMissing(User user, List<OnboardingRequestResponse> requests) {
        List<String> missing = new ArrayList<>();
        if (user.getRole() == Role.CAREGIVER) {
            CaregiverProfile profile = caregiverProfileRepository.findByUserId(user.getId()).orElse(null);
            if (profile == null
                    || profile.getQualifications() == null
                    || profile.getQualifications().isEmpty()) {
                missing.add("Add at least one qualification");
            } else if (profile.getQualifications().contains(Qualification.OTHER)
                    && (profile.getOtherQualificationDetail() == null
                    || profile.getOtherQualificationDetail().isBlank())) {
                missing.add("Specify your Other qualification");
            }
            if (profile == null
                    || isBlank(profile.getHomeAddressLine())
                    || isBlank(profile.getHomeCity())
                    || isBlank(profile.getHomeZip())
                    || profile.getHomeLat() == null
                    || profile.getHomeLng() == null) {
                missing.add("Add your home address");
            }
            boolean hasPhoto = profile != null
                    && profile.getProfilePhotoUrl() != null
                    && !profile.getProfilePhotoUrl().isBlank();
            if (!hasPhoto) {
                missing.add("Upload a profile photo");
            }
        } else if (user.getRole() == Role.CLIENT) {
            ClientProfile profile = clientProfileRepository.findByUserId(user.getId()).orElse(null);
            if (profile == null
                    || isBlank(profile.getAddressLine())
                    || isBlank(profile.getCity())
                    || isBlank(profile.getZip())) {
                missing.add("Add the care address");
            }
        }
        if (requests.stream().anyMatch(r -> r.status() == OnboardingRequestStatus.OPEN)) {
            missing.add("Submit the information requested below");
        }
        return missing;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Transactional
    public OnboardingRequestResponse createAdminRequest(UUID targetUserId, CreateOnboardingRequest body, User admin) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (target.getRole() != Role.CAREGIVER && target.getRole() != Role.CLIENT) {
            throw new BadRequestException("Onboarding requests apply to caregivers and clients only");
        }
        if (target.getStatus() != UserStatus.PENDING_REVIEW && target.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("User must have verified email before requesting onboarding info");
        }
        if (target.getStatus() == UserStatus.ACTIVE) {
            target.setStatus(UserStatus.PENDING_REVIEW);
        }
        target.setApplicationSubmittedAt(null);
        if (body.fieldType() == OnboardingFieldType.PROFILE_PHOTO && target.getRole() != Role.CAREGIVER) {
            throw new BadRequestException("Profile photo requests are for caregiver accounts only");
        }
        OnboardingRequest saved = requestRepository.save(OnboardingRequest.builder()
                .userId(target.getId())
                .requestedByUserId(admin.getId())
                .title(body.title().trim())
                .instructions(body.instructions() == null ? null : body.instructions().trim())
                .fieldType(body.fieldType())
                .status(OnboardingRequestStatus.OPEN)
                .build());
        auditLogService.record(admin, AuditAction.ONBOARDING_INFO_REQUESTED, "USER", target.getId(), null,
                "requestId=" + saved.getId() + " type=" + saved.getFieldType());
        notificationService.notifyUser(
                target,
                NotificationType.ONBOARDING_INFO_REQUESTED,
                "Additional information requested",
                "Please provide: " + saved.getTitle(),
                null);
        return toResponse(saved);
    }

    @Transactional
    public OnboardingRequestResponse submitText(User actor, UUID requestId, String responseText) {
        OnboardingRequest req = requireOwnOpen(actor, requestId);
        if (req.getFieldType() != OnboardingFieldType.TEXT) {
            throw new BadRequestException("This request needs a file upload, not text");
        }
        if (responseText == null || responseText.isBlank()) {
            throw new BadRequestException("Enter the requested information");
        }
        req.setResponseText(responseText.trim());
        req.setStatus(OnboardingRequestStatus.SUBMITTED);
        req.setSubmittedAt(Instant.now());
        return toResponse(req);
    }

    @Transactional
    public OnboardingRequestResponse submitFile(User actor, UUID requestId, MultipartFile file) {
        OnboardingRequest req = requireOwnOpen(actor, requestId);
        if (req.getFieldType() == OnboardingFieldType.TEXT) {
            throw new BadRequestException("This request needs a text response");
        }
        String url;
        if (req.getFieldType() == OnboardingFieldType.PROFILE_PHOTO) {
            if (actor.getRole() != Role.CAREGIVER) {
                throw new BadRequestException("Profile photo uploads are for caregiver accounts only");
            }
            url = fileStorageService.storeProfilePhoto(actor.getId(), file);
            CaregiverProfile profile = caregiverProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
            profile.setProfilePhotoUrl(url);
        } else {
            url = fileStorageService.storeOnboardingDocument(actor.getId(), file);
        }
        req.setFileUrl(url);
        req.setStatus(OnboardingRequestStatus.SUBMITTED);
        req.setSubmittedAt(Instant.now());
        return toResponse(req);
    }

    @Transactional
    public void approveReview(UUID targetUserId, User admin) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (target.getRole() != Role.CAREGIVER && target.getRole() != Role.CLIENT) {
            throw new BadRequestException("Only caregivers and clients go through OkayNow review");
        }
        if (!target.isEmailVerified()) {
            throw new BadRequestException("User has not verified their email yet");
        }
        long open = requestRepository.countByUserIdAndStatus(target.getId(), OnboardingRequestStatus.OPEN);
        if (open > 0) {
            throw new BadRequestException(
                    "There are still open information requests. Cancel or wait for submission before approving.");
        }
        for (OnboardingRequest req : requestRepository.findByUserIdAndStatusInOrderByCreatedAtAsc(
                target.getId(), List.of(OnboardingRequestStatus.SUBMITTED))) {
            req.setStatus(OnboardingRequestStatus.ACCEPTED);
            req.setResolvedAt(Instant.now());
        }
        target.setStatus(UserStatus.ACTIVE);
        target.setApplicationSubmittedAt(null);
        auditLogService.record(admin, AuditAction.ACCOUNT_REVIEW_APPROVED, "USER", target.getId(), null, null);
        notificationService.notifyUser(
                target,
                NotificationType.ACCOUNT_APPROVED,
                "You're verified",
                "Your OkayNow account has been approved. You can now use the full platform.",
                null);
    }

    @Transactional
    public OnboardingRequestResponse cancelRequest(UUID requestId, User admin) {
        OnboardingRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Onboarding request not found"));
        if (req.getStatus() != OnboardingRequestStatus.OPEN
                && req.getStatus() != OnboardingRequestStatus.SUBMITTED) {
            throw new BadRequestException("Only open or submitted requests can be cancelled");
        }
        req.setStatus(OnboardingRequestStatus.CANCELLED);
        req.setResolvedAt(Instant.now());
        return toResponse(req);
    }

    @Transactional(readOnly = true)
    public List<OnboardingRequestResponse> listForUser(UUID userId) {
        return requestRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    private OnboardingRequest requireOwnOpen(User actor, UUID requestId) {
        OnboardingRequest req = requestRepository.findByIdAndUserId(requestId, actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Onboarding request not found"));
        if (req.getStatus() != OnboardingRequestStatus.OPEN) {
            throw new BadRequestException(
                    "This item is locked after submission. Wait for the agency if a resubmission is needed.");
        }
        return req;
    }

    @Transactional
    public OnboardingRequestResponse requestResubmit(UUID requestId, User admin) {
        OnboardingRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Onboarding request not found"));
        if (req.getStatus() != OnboardingRequestStatus.SUBMITTED
                && req.getStatus() != OnboardingRequestStatus.ACCEPTED) {
            throw new BadRequestException("Only submitted or accepted items can be reopened for resubmission");
        }
        User target = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (target.getStatus() == UserStatus.ACTIVE) {
            target.setStatus(UserStatus.PENDING_REVIEW);
        }
        target.setApplicationSubmittedAt(null);
        req.setStatus(OnboardingRequestStatus.OPEN);
        req.setResolvedAt(null);
        // Keep prior response/file visible until the applicant replaces them.
        auditLogService.record(admin, AuditAction.ONBOARDING_RESUBMIT_REQUESTED, "USER", target.getId(), null,
                "requestId=" + req.getId() + " type=" + req.getFieldType());
        notificationService.notifyUser(
                target,
                NotificationType.ONBOARDING_INFO_REQUESTED,
                "Please resubmit: " + req.getTitle(),
                "The agency asked you to update this item. Open your application to resubmit.",
                null);
        return toResponse(req);
    }

    private OnboardingRequestResponse toResponse(OnboardingRequest req) {
        return new OnboardingRequestResponse(
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
