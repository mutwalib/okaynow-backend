package com.okaynow.notifications.service;

import com.okaynow.notifications.domain.NotificationType;
import com.okaynow.notifications.event.ShiftLifecycleEvent;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationFanoutListener {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final ShiftRepository shiftRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onShiftLifecycle(ShiftLifecycleEvent event) {
        Shift shift = shiftRepository.findById(event.shiftId()).orElse(null);
        if (shift != null) {
            notificationService.broadcastShiftBoard(event.type().name(), shift);
        } else {
            notificationService.broadcastShiftBoard(
                    event.type().name(),
                    event.shiftId(),
                    event.status(),
                    event.clientProfileId());
        }

        String payload = buildPayload(event, shift);

        Set<UUID> notified = new HashSet<>();
        for (User admin : userRepository.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)) {
            notifyOnce(notified, admin, event, payload);
        }

        if (event.clientProfileId() != null) {
            clientProfileRepository.findByIdWithUser(event.clientProfileId()).ifPresent(client -> {
                if (client.getUser() != null) {
                    notifyOnce(notified, client.getUser(), event, payload);
                }
            });
        }

        if (event.caregiverUserId() != null) {
            userRepository.findById(event.caregiverUserId()).ifPresent(cg ->
                    notifyOnce(notified, cg, event, payload));
        }

        // New open shifts ping every active caregiver (Uber-style open-board nudge).
        if (event.type() == NotificationType.SHIFT_POSTED) {
            for (User caregiver : userRepository.findByRoleAndStatus(Role.CAREGIVER, UserStatus.ACTIVE)) {
                notifyOnce(notified, caregiver, event, payload);
            }
        }
    }

    private static String buildPayload(ShiftLifecycleEvent event, Shift shift) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"shiftId\":\"").append(event.shiftId()).append("\"");
        sb.append(",\"status\":\"").append(event.status()).append("\"");
        sb.append(",\"action\":\"").append(event.type().name()).append("\"");
        if (shift != null) {
            if (shift.getCity() != null) {
                sb.append(",\"city\":\"").append(escapeJson(shift.getCity())).append("\"");
            }
            if (shift.getDate() != null) {
                sb.append(",\"date\":\"").append(shift.getDate()).append("\"");
            }
            if (shift.getRequiredQualification() != null) {
                sb.append(",\"qualification\":\"")
                        .append(shift.getRequiredQualification())
                        .append("\"");
            }
            if (shift.getPayRate() != null) {
                sb.append(",\"payRate\":").append(shift.getPayRate());
            }
            sb.append(",\"marketplaceSlots\":").append(shift.getMarketplaceSlots());
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void notifyOnce(
            Set<UUID> notified,
            User user,
            ShiftLifecycleEvent event,
            String payload) {
        if (user == null || user.getId() == null || !notified.add(user.getId())) {
            return;
        }
        notificationService.notifyUser(user, event.type(), event.title(), event.body(), payload);
    }
}
