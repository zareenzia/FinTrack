package org.example.finzin.service;

import org.example.finzin.entity.NotificationEntity;
import org.example.finzin.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public NotificationEntity create(Long userId, String type, String title, String message,
                                      String relatedEntityType, Long relatedEntityId) {
        NotificationEntity entity = new NotificationEntity();
        entity.setUserId(userId);
        entity.setType(type);
        entity.setTitle(title);
        entity.setMessage(message);
        entity.setRelatedEntityType(relatedEntityType);
        entity.setRelatedEntityId(relatedEntityId);
        entity.setIsRead(false);
        return notificationRepository.save(entity);
    }

    public List<NotificationEntity> getForUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    public NotificationEntity markRead(Long id, Long userId) {
        NotificationEntity entity = notificationRepository.findById(id).orElse(null);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return null;
        }
        entity.setIsRead(true);
        return notificationRepository.save(entity);
    }
}
