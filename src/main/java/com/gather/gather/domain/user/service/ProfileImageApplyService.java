package com.gather.gather.domain.user.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.user.entity.ProfileImageUpload;
import com.gather.gather.domain.user.repository.ProfileImageUploadRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.infra.s3.StoredObjectMetadata;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileImageApplyService {

    private final UserRepository userRepository;
    private final ProfileImageUploadRepository profileImageUploadRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void apply(
            Long userId,
            String objectKey,
            ProfileImageFormat keyFormat,
            StoredObjectMetadata metadata) {
        User user =
                userRepository
                        .findByIdForUpdate(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        ProfileImageUpload upload =
                profileImageUploadRepository
                        .findByUserIdAndObjectKeyForUpdate(userId, objectKey)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.INVALID_PROFILE_IMAGE_KEY));
        LocalDateTime now = LocalDateTime.now();
        // S3 검증은 DB 락을 오래 점유하지 않도록 앞 단계에서 수행했으므로, 락 획득 후 상태를 다시 확인한다.
        upload.validatePendingSession(now, keyFormat.contentType());
        if (metadata.contentLength() != upload.getExpectedSize()) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_SIZE_MISMATCH);
        }

        String previousObjectKey = user.getProfileImageKey();
        user.changeProfileImageKey(objectKey);
        String deletionTarget =
                Objects.equals(previousObjectKey, objectKey) ? null : previousObjectKey;
        upload.apply(deletionTarget, now);
        if (deletionTarget != null) {
            eventPublisher.publishEvent(new ProfileImageReplacedEvent(upload.getId()));
        } else {
            profileImageUploadRepository.delete(upload);
        }
    }
}
