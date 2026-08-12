package com.okaynow.users.service;

import com.okaynow.audit.domain.AuditAction;
import com.okaynow.audit.service.AuditLogService;
import com.okaynow.auth.repository.AuthChallengeRepository;
import com.okaynow.booking.domain.ShiftClaim;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.booking.repository.ShiftClaimRepository;
import com.okaynow.booking.service.BookingService;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Self-service account deletion (App Store Guideline 5.1.1(v)).
 * Soft-deletes the login identity while releasing open caregiver claims.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private static final EnumSet<ShiftClaimStatus> ACTIVE_CLAIMS =
            EnumSet.of(ShiftClaimStatus.PENDING, ShiftClaimStatus.CONFIRMED);

    private final UserRepository userRepository;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final ShiftClaimRepository shiftClaimRepository;
    private final BookingService bookingService;
    private final AuthChallengeRepository authChallengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional
    public void deleteOwnAccount(User actor) {
        if (actor.getRole() == Role.ADMIN) {
            throw new BadRequestException(
                    "Platform owner accounts cannot be deleted from the app. Contact support.");
        }
        if (actor.getStatus() == UserStatus.DEACTIVATED) {
            return;
        }

        String originalEmail = actor.getEmail();

        if (actor.getRole() == Role.CAREGIVER) {
            releaseCaregiverCommitments(actor);
            scrubCaregiverProfile(actor.getId());
        }

        authChallengeRepository.consumeAllOpenChallenges(actor.getId(), Instant.now());

        auditLogService.record(
                actor,
                AuditAction.ACCOUNT_DELETED,
                "USER",
                actor.getId(),
                null,
                "self-service delete formerEmail=" + originalEmail);

        actor.setStatus(UserStatus.DEACTIVATED);
        actor.setEmailVerified(false);
        actor.setEmailVerifiedAt(null);
        actor.setPhone(null);
        actor.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        // Free the email for future re-registration; keep a unique tombstone address.
        actor.setEmail("deleted+" + actor.getId() + "@deleted.okaynow.local");
        userRepository.save(actor);
    }

    private void releaseCaregiverCommitments(User actor) {
        CaregiverProfile profile = caregiverProfileRepository.findByUserId(actor.getId())
                .orElse(null);
        if (profile == null) {
            return;
        }
        List<ShiftClaim> active = shiftClaimRepository
                .findByCaregiverProfileIdAndStatusIn(profile.getId(), ACTIVE_CLAIMS);
        for (ShiftClaim claim : active) {
            ShiftStatus shiftStatus = claim.getShift().getStatus();
            if (shiftStatus == ShiftStatus.IN_PROGRESS) {
                throw new BadRequestException(
                        "You have a shift in progress. Clock out or contact the agency before deleting your account.");
            }
            bookingService.cancel(claim.getId(), "Account deleted by caregiver");
        }
    }

    private void scrubCaregiverProfile(UUID userId) {
        caregiverProfileRepository.findByUserId(userId).ifPresent(profile -> {
            profile.setFirstName("Deleted");
            profile.setLastName("User");
            profile.setProfilePhotoUrl(null);
            profile.setHomeLat(null);
            profile.setHomeLng(null);
            profile.setHourlyRateMin(null);
            profile.setHourlyRateMax(null);
            profile.setServiceRadiusMiles(null);
            if (profile.getQualifications() != null) {
                profile.getQualifications().clear();
            }
        });
    }
}
