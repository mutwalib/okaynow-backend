package com.okaynow.notifications.event;

import com.okaynow.notifications.domain.NotificationType;
import com.okaynow.shifts.domain.ShiftStatus;

import java.util.UUID;

public record ShiftLifecycleEvent(
        NotificationType type,
        UUID shiftId,
        ShiftStatus status,
        UUID clientProfileId,
        UUID caregiverUserId,
        String title,
        String body
) {
}
