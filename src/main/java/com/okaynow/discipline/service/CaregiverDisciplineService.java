package com.okaynow.discipline.service;

import com.okaynow.audit.domain.AuditAction;
import com.okaynow.audit.service.AuditLogService;
import com.okaynow.discipline.domain.CaregiverWarning;
import com.okaynow.discipline.dto.NoShowDisciplineResult;
import com.okaynow.discipline.repository.CaregiverWarningRepository;
import com.okaynow.notifications.domain.NotificationType;
import com.okaynow.notifications.service.NotificationService;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues formal no-show warnings and auto-restricts after the third warning.
 * Super admins ({@link Role#ADMIN}) are notified in realtime via the notification queue.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CaregiverDisciplineService {

    /** Third no-show warning triggers automatic platform restriction. */
    public static final int MAX_WARNINGS_BEFORE_RESTRICTION = 3;

    private final CaregiverWarningRepository warningRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Transactional
    public NoShowDisciplineResult recordNoShow(
            CaregiverProfile caregiver, Shift shift, String reason, User actor) {
        if (warningRepository.existsByShiftId(shift.getId())) {
            CaregiverWarning existing = warningRepository.findByShiftId(shift.getId()).orElseThrow();
            boolean restricted = caregiver.getUser() != null
                    && caregiver.getUser().getStatus() == UserStatus.RESTRICTED;
            return new NoShowDisciplineResult(
                    existing.getWarningNumber(), restricted, MAX_WARNINGS_BEFORE_RESTRICTION);
        }

        User caregiverUser = caregiver.getUser();
        int warningNumber = (int) warningRepository.countByCaregiverProfileId(caregiver.getId()) + 1;
        String safeReason = (reason == null || reason.isBlank()) ? "Marked no-show" : reason.trim();

        CaregiverWarning warning = warningRepository.save(CaregiverWarning.builder()
                .caregiverProfileId(caregiver.getId())
                .userId(caregiverUser.getId())
                .shiftId(shift.getId())
                .warningNumber(warningNumber)
                .reason(safeReason.length() > 500 ? safeReason.substring(0, 500) : safeReason)
                .build());

        auditLogService.record(
                actor,
                AuditAction.CAREGIVER_NO_SHOW_WARNING,
                "CAREGIVER",
                caregiver.getId(),
                shift.getClientProfileId() != null ? shift.getClientProfileId() : shift.getFacilityProfileId(),
                "warning=%s/%s shift=%s reason=%s".formatted(
                        warningNumber, MAX_WARNINGS_BEFORE_RESTRICTION, shift.getId(), safeReason));

        String caregiverName = caregiver.getFirstName() + " " + caregiver.getLastName();
        String payload = buildPayload(
                shift.getId(), caregiver.getId(), caregiverUser.getId(), warningNumber, false);

        String warningTitle = "No-show warning " + warningNumber + " of " + MAX_WARNINGS_BEFORE_RESTRICTION;
        String warningBodyForCaregiver = warningNumber >= MAX_WARNINGS_BEFORE_RESTRICTION
                ? ("Your shift on " + shift.getDate() + " was marked no-show. "
                + "This is warning " + warningNumber + " of " + MAX_WARNINGS_BEFORE_RESTRICTION
                + ". Your account is being restricted from claiming or receiving new shifts.")
                : ("Your shift on " + shift.getDate() + " was marked no-show. "
                + "This is formal warning " + warningNumber + " of " + MAX_WARNINGS_BEFORE_RESTRICTION
                + ". Additional no-shows may lead to account restriction, which blocks new shifts "
                + "until OkayNow lifts the restriction.");

        String warningBodyForAdmin = caregiverName + " received no-show warning "
                + warningNumber + " of " + MAX_WARNINGS_BEFORE_RESTRICTION
                + " for the " + shift.getDate() + " shift"
                + (shift.getCity() != null ? " in " + shift.getCity() : "")
                + ". " + safeReason;

        notificationService.notifyUser(
                caregiverUser,
                NotificationType.CAREGIVER_NO_SHOW_WARNING,
                warningTitle,
                warningBodyForCaregiver,
                payload);

        for (User admin : userRepository.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)) {
            notificationService.notifyUser(
                    admin,
                    NotificationType.CAREGIVER_NO_SHOW_WARNING,
                    "No-show: " + caregiverName + " (warning " + warningNumber + "/"
                            + MAX_WARNINGS_BEFORE_RESTRICTION + ")",
                    warningBodyForAdmin,
                    payload);
        }

        boolean restricted = false;
        if (warningNumber >= MAX_WARNINGS_BEFORE_RESTRICTION
                && caregiverUser.getStatus() == UserStatus.ACTIVE) {
            caregiverUser.setStatus(UserStatus.RESTRICTED);
            userRepository.save(caregiverUser);
            restricted = true;

            auditLogService.record(
                    actor,
                    AuditAction.CAREGIVER_AUTO_RESTRICTED,
                    "USER",
                    caregiverUser.getId(),
                    null,
                    "auto-restricted after %s no-show warnings (shift=%s)"
                            .formatted(warningNumber, shift.getId()));

            String restrictPayload = buildPayload(
                    shift.getId(), caregiver.getId(), caregiverUser.getId(), warningNumber, true);
            String restrictTitle = "Account restricted after repeated no-shows";
            String restrictBodyCaregiver =
                    "Your OkayNow account has been restricted after "
                            + warningNumber + " no-show warnings. You can still sign in and manage "
                            + "existing shifts, but you cannot claim or receive new shifts until "
                            + "a platform admin lifts the restriction.";
            String restrictBodyAdmin = caregiverName
                    + " was automatically restricted after "
                    + warningNumber + " no-show warnings.";

            notificationService.notifyUser(
                    caregiverUser,
                    NotificationType.CAREGIVER_AUTO_RESTRICTED,
                    restrictTitle,
                    restrictBodyCaregiver,
                    restrictPayload);

            for (User admin : userRepository.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)) {
                notificationService.notifyUser(
                        admin,
                        NotificationType.CAREGIVER_AUTO_RESTRICTED,
                        "Caregiver auto-restricted: " + caregiverName,
                        restrictBodyAdmin,
                        restrictPayload);
            }

            log.info("Auto-restricted caregiver {} after {} no-show warning(s)",
                    caregiver.getId(), warningNumber);
        }

        return new NoShowDisciplineResult(warningNumber, restricted, MAX_WARNINGS_BEFORE_RESTRICTION);
    }

    private static String buildPayload(
            java.util.UUID shiftId,
            java.util.UUID caregiverProfileId,
            java.util.UUID caregiverUserId,
            int warningNumber,
            boolean restricted) {
        return "{\"shiftId\":\"" + shiftId + "\""
                + ",\"caregiverProfileId\":\"" + caregiverProfileId + "\""
                + ",\"caregiverUserId\":\"" + caregiverUserId + "\""
                + ",\"warningNumber\":" + warningNumber
                + ",\"maxWarnings\":" + MAX_WARNINGS_BEFORE_RESTRICTION
                + ",\"restricted\":" + restricted
                + ",\"action\":\"NO_SHOW_DISCIPLINE\"}";
    }
}
