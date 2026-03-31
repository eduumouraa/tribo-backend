package com.tribo.modules.notification.service;

import com.tribo.modules.notification.entity.Notification;
import com.tribo.modules.notification.repository.NotificationRepository;
import com.tribo.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Async
    @Transactional
    public void create(UUID userId, String type, String title, String message) {
        create(userId, type, title, message, null);
    }

    @Async
    @Transactional
    public void create(UUID userId, String type, String title, String message, Map<String, Object> data) {
        Notification n = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .data(data)
                .build();
        notificationRepository.save(n);
        log.debug("Notificação criada para userId={} type={}", userId, type);
    }

    @Transactional(readOnly = true)
    public List<Notification> listByUser(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificação não encontrada"));
        if (!n.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Notificação não encontrada");
        }
        n.setIsRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllReadByUserId(userId);
    }
}
