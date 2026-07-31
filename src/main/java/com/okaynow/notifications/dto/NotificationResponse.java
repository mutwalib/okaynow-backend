package com.okaynow.notifications.dto;

import com.okaynow.notifications.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        String payload,
        Instant readAt,
        Instant createdAt
) {
}
