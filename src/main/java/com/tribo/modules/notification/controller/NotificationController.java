package com.tribo.modules.notification.controller;

import com.tribo.modules.notification.entity.Notification;
import com.tribo.modules.notification.service.NotificationService;
import com.tribo.modules.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notificações", description = "Central de notificações do aluno")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Listar todas as notificações do usuário")
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                notificationService.listByUser(user.getId())
                        .stream().map(this::toResponse).toList()
        );
    }

    @Operation(summary = "Quantidade de notificações não lidas")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread(user.getId())));
    }

    @Operation(summary = "Marcar uma notificação como lida")
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        notificationService.markRead(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Marcar todas as notificações como lidas")
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal User user) {
        notificationService.markAllRead(user.getId());
        return ResponseEntity.ok().build();
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId().toString(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getIsRead(),
                n.getData(),
                n.getCreatedAt()
        );
    }

    public record NotificationResponse(
            String id, String type, String title, String message,
            boolean isRead, Map<String, Object> data, OffsetDateTime createdAt
    ) {}
}
