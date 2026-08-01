package com.gather.gather.domain.user.service;

import com.gather.gather.domain.user.entity.ProfileImageUpload;
import com.gather.gather.domain.user.repository.ProfileImageUploadRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileImageDeletionService {

    private static final String TRACKING_KEY_PREFIX = "__PROFILE_IMAGE_DELETION_TASK__";

    private final ProfileImageUploadRepository profileImageUploadRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleDeletion(Long userId, String profileImageKey, LocalDateTime now) {
        if (profileImageKey == null) {
            return;
        }

        String trackingKey = "%s/%d/%s".formatted(TRACKING_KEY_PREFIX, userId, UUID.randomUUID());
        ProfileImageUpload task =
                profileImageUploadRepository.save(
                        ProfileImageUpload.createDeletionTask(
                                userId, trackingKey, profileImageKey, now));
        eventPublisher.publishEvent(new ProfileImageDeletionRequestedEvent(task.getId()));
    }
}
