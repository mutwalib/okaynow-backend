package com.okaynow.notifications.service;

import com.okaynow.notifications.domain.NotificationType;
import com.okaynow.notifications.event.ShiftLifecycleEvent;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShiftEventPublisher {

    private final ApplicationEventPublisher events;

    public void publish(
            NotificationType type,
            Shift shift,
            UUID caregiverUserId,
            String title,
            String body) {
        events.publishEvent(new ShiftLifecycleEvent(
                type,
                shift.getId(),
                shift.getStatus(),
                shift.getClientProfileId(),
                caregiverUserId,
                title,
                body));
    }

    public void publish(
            NotificationType type,
            UUID shiftId,
            ShiftStatus status,
            UUID clientProfileId,
            UUID caregiverUserId,
            String title,
            String body) {
        events.publishEvent(new ShiftLifecycleEvent(
                type, shiftId, status, clientProfileId, caregiverUserId, title, body));
    }
}
