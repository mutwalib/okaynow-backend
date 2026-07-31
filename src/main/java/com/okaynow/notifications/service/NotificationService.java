package com.okaynow.notifications.service;

import com.okaynow.common.dto.PagedResponse;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.notifications.domain.Notification;
import com.okaynow.notifications.domain.NotificationType;
import com.okaynow.notifications.dto.NotificationResponse;
import com.okaynow.notifications.dto.ShiftBoardUpdate;
import com.okaynow.notifications.repository.NotificationRepository;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.users.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> listMine(UUID userId, Pageable pageable) {
        return PagedResponse.from(
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                        .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        return toResponse(notification);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId, Instant.now());
    }

    /**
     * Persists an in-app notification and pushes it to the user's private queue.
     * Call after the surrounding business transaction has committed.
     */
    @Transactional
    public NotificationResponse notifyUser(
            User user,
            NotificationType type,
            String title,
            String body,
            String payloadJson) {
        Notification saved = notificationRepository.save(Notification.builder()
                .userId(user.getId())
                .type(type)
                .title(title)
                .body(body)
                .payload(payloadJson)
                .build());
        NotificationResponse response = toResponse(saved);
        messagingTemplate.convertAndSendToUser(
                user.getEmail(), "/queue/notifications", response);
        return response;
    }

    public void broadcastShiftBoard(String action, Shift shift) {
        messagingTemplate.convertAndSend("/topic/shifts", new ShiftBoardUpdate(
                action,
                shift.getId(),
                shift.getStatus(),
                shift.getClientProfileId(),
                shift.getCity(),
                shift.getDate(),
                shift.getRequiredQualification(),
                shift.getPayRate(),
                shift.getMarketplaceSlots(),
                Instant.now()));
    }

    public void broadcastShiftBoard(
            String action,
            UUID shiftId,
            ShiftStatus status,
            UUID clientProfileId) {
        messagingTemplate.convertAndSend("/topic/shifts", new ShiftBoardUpdate(
                action, shiftId, status, clientProfileId,
                null, null, null, null, null, Instant.now()));
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getBody(),
                n.getPayload(),
                n.getReadAt(),
                n.getCreatedAt());
    }
}
