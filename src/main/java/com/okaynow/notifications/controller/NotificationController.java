package com.okaynow.notifications.controller;

import com.okaynow.common.dto.PagedResponse;
import com.okaynow.notifications.dto.NotificationResponse;
import com.okaynow.notifications.service.NotificationService;
import com.okaynow.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<PagedResponse<NotificationResponse>> mine(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        UUID userId = userService.getByEmail(authentication.getName()).getId();
        return ResponseEntity.ok(notificationService.listMine(
                userId, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/me/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication authentication) {
        UUID userId = userService.getByEmail(authentication.getName()).getId();
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(userId)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(
            Authentication authentication, @PathVariable UUID id) {
        UUID userId = userService.getByEmail(authentication.getName()).getId();
        return ResponseEntity.ok(notificationService.markRead(userId, id));
    }

    @PostMapping("/me/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(Authentication authentication) {
        UUID userId = userService.getByEmail(authentication.getName()).getId();
        return ResponseEntity.ok(Map.of("updated", notificationService.markAllRead(userId)));
    }
}
