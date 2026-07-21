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
        validateUploadSession(upload, keyFormat, metadata, now);

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

    private void validateUploadSession(
            ProfileImageUpload upload,
            ProfileImageFormat keyFormat,
            StoredObjectMetadata metadata,
            LocalDateTime now) {
        if (!upload.isPending()) {
            throw new BusinessException(ErrorCode.INVALID_PROFILE_IMAGE_KEY);
        }
        if (upload.isExpired(now)) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_UPLOAD_EXPIRED);
        }
        ProfileImageFormat issuedFormat =
                ProfileImageFormat.fromContentType(upload.getContentType());
        if (issuedFormat != keyFormat) {
            throw new BusinessException(ErrorCode.INVALID_PROFILE_IMAGE_KEY);
        }
        if (metadata.contentLength() != upload.getExpectedSize()) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_SIZE_MISMATCH);
        }
    }
}
