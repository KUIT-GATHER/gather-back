package com.gather.gather.domain.user.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.user.dto.ProfileImageCurrentResponse;
import com.gather.gather.domain.user.dto.ProfileImagePresignedUrlRequest;
import com.gather.gather.domain.user.dto.ProfileImagePresignedUrlResponse;
import com.gather.gather.domain.user.dto.ProfileImageUpdateRequest;
import com.gather.gather.domain.user.dto.ProfileImageUpdateResponse;
import com.gather.gather.domain.user.entity.ProfileImageUpload;
import com.gather.gather.domain.user.entity.ProfileImageUploadStatus;
import com.gather.gather.domain.user.repository.ProfileImageUploadRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.infra.s3.ObjectStorage;
import com.gather.gather.global.infra.s3.S3Properties;
import com.gather.gather.global.infra.s3.StoredObjectMetadata;
import com.gather.gather.global.util.SecurityUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileImageService {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final UserRepository userRepository;
    private final ProfileImageUploadRepository profileImageUploadRepository;
    private final ObjectStorage objectStorage;
    private final S3Properties properties;
    private final ProfileImageUrlResolver profileImageUrlResolver;
    private final ProfileImageContentValidator profileImageContentValidator;
    private final ProfileImageApplyService profileImageApplyService;

    @Transactional(readOnly = true)
    public ProfileImageCurrentResponse getCurrentProfileImage() {
        Long userId = SecurityUtil.getCurrentUserId();
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new ProfileImageCurrentResponse(
                profileImageUrlResolver.resolve(user.getProfileImageKey()));
    }

    @Transactional
    public ProfileImagePresignedUrlResponse createPresignedUrl(
            ProfileImagePresignedUrlRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        ProfileImageFormat format = ProfileImageFormat.fromContentType(request.contentType());
        validateRequestedFileSize(request.fileSize());
        userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        validatePendingUploadLimit(userId, now);

        String objectKey =
                properties.objectPrefix()
                        + "/"
                        + userId
                        + "/"
                        + UUID.randomUUID()
                        + "."
                        + format.extension();
        Duration expiration = Duration.ofSeconds(properties.presignedUrlExpirationSeconds());
        profileImageUploadRepository.save(
                ProfileImageUpload.create(
                        userId,
                        objectKey,
                        format.contentType(),
                        request.fileSize(),
                        now.plusSeconds(properties.presignedUrlExpirationSeconds()),
                        now));
        String uploadUrl =
                objectStorage.createPresignedPutUrl(
                        objectKey, format.contentType(), request.fileSize(), expiration);

        return new ProfileImagePresignedUrlResponse(
                uploadUrl,
                objectKey,
                profileImageUrlResolver.resolve(objectKey),
                properties.presignedUrlExpirationSeconds());
    }

    public ProfileImageUpdateResponse updateProfileImage(ProfileImageUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        ProfileImageFormat keyFormat = validateAndGetKeyFormat(request.objectKey(), userId);
        ProfileImageUpload upload =
                profileImageUploadRepository
                        .findByUserIdAndObjectKey(userId, request.objectKey())
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.INVALID_PROFILE_IMAGE_KEY));
        LocalDateTime now = LocalDateTime.now();
        validateUploadSession(upload, keyFormat, now);
        StoredObjectMetadata metadata = objectStorage.getMetadata(request.objectKey());
        validateStoredObject(metadata, keyFormat, upload);
        byte[] content = objectStorage.getContent(request.objectKey(), metadata.eTag());
        if (content.length != metadata.contentLength()) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_SIZE_MISMATCH);
        }
        profileImageContentValidator.validate(keyFormat, content);
        profileImageApplyService.apply(userId, request.objectKey(), keyFormat, metadata);

        return new ProfileImageUpdateResponse(profileImageUrlResolver.resolve(request.objectKey()));
    }

    private void validateRequestedFileSize(Long fileSize) {
        if (fileSize == null || fileSize <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (fileSize > properties.maxImageSizeBytes()) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_SIZE_EXCEEDED);
        }
    }

    private void validatePendingUploadLimit(Long userId, LocalDateTime now) {
        long pendingUploadCount =
                profileImageUploadRepository.countByUserIdAndStatusAndExpiresAtAfter(
                        userId, ProfileImageUploadStatus.PENDING, now);
        if (pendingUploadCount >= properties.maxPendingUploadsPerUser()) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_UPLOAD_LIMIT_EXCEEDED);
        }
    }

    private void validateUploadSession(
            ProfileImageUpload upload, ProfileImageFormat keyFormat, LocalDateTime now) {
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
    }

    private ProfileImageFormat validateAndGetKeyFormat(String objectKey, Long userId) {
        if (objectKey == null) {
            throw new BusinessException(ErrorCode.INVALID_PROFILE_IMAGE_KEY);
        }
        String expectedPrefix = properties.objectPrefix() + "/" + userId + "/";
        Pattern pattern =
                Pattern.compile(
                        "^"
                                + Pattern.quote(expectedPrefix)
                                + "("
                                + UUID_PATTERN.pattern()
                                + ")\\.(jpg|png|webp)$");
        Matcher matcher = pattern.matcher(objectKey);
        if (!matcher.matches()) {
            throw new BusinessException(ErrorCode.INVALID_PROFILE_IMAGE_KEY);
        }
        return ProfileImageFormat.fromExtension(matcher.group(2));
    }

    private void validateStoredObject(
            StoredObjectMetadata metadata,
            ProfileImageFormat keyFormat,
            ProfileImageUpload upload) {
        ProfileImageFormat storedFormat =
                ProfileImageFormat.fromContentType(metadata.contentType());
        if (storedFormat != keyFormat) {
            throw new BusinessException(ErrorCode.INVALID_PROFILE_IMAGE_KEY);
        }
        if (metadata.contentLength() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (metadata.contentLength() > properties.maxImageSizeBytes()) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_SIZE_EXCEEDED);
        }
        if (metadata.contentLength() != upload.getExpectedSize()) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_SIZE_MISMATCH);
        }
    }
}
