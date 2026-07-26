package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.meeting.dto.MeetingImageListResponse;
import com.gather.gather.domain.meeting.dto.MeetingImagePresignedUrlRequest;
import com.gather.gather.domain.meeting.dto.MeetingImagePresignedUrlResponse;
import com.gather.gather.domain.meeting.dto.MeetingImageUpdateRequest;
import com.gather.gather.domain.meeting.dto.MeetingImageUpdateResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingImage;
import com.gather.gather.domain.meeting.entity.MeetingImageUpload;
import com.gather.gather.domain.meeting.entity.MeetingImageUploadStatus;
import com.gather.gather.domain.meeting.repository.MeetingImageRepository;
import com.gather.gather.domain.meeting.repository.MeetingImageUploadRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.infra.s3.ObjectStorage;
import com.gather.gather.global.infra.s3.S3Properties;
import com.gather.gather.global.infra.s3.StoredObjectMetadata;
import com.gather.gather.global.util.SecurityUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeetingImageService {

    private static final int MAX_IMAGES = 3;
    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final MeetingRepository meetingRepository;
    private final MeetingImageRepository meetingImageRepository;
    private final MeetingImageUploadRepository meetingImageUploadRepository;
    private final MeetingImageApplyService meetingImageApplyService;
    private final MeetingImageContentValidator imageContentValidator;
    private final MeetingImageUrlResolver urlResolver;
    private final ObjectStorage objectStorage;
    private final S3Properties properties;

    @Transactional(readOnly = true)
    public MeetingImageListResponse getImages(Long meetingId) {
        if (!meetingRepository.existsById(meetingId)) {
            throw new BusinessException(ErrorCode.MEETING_NOT_FOUND);
        }
        List<String> urls =
                meetingImageRepository.findByMeetingIdOrderBySortOrderAsc(meetingId).stream()
                        .map(MeetingImage::getObjectKey)
                        .map(urlResolver::resolve)
                        .toList();
        return new MeetingImageListResponse(urls);
    }

    @Transactional
    public MeetingImagePresignedUrlResponse createPresignedUrl(
            Long meetingId, MeetingImagePresignedUrlRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        MeetingImageFormat format = MeetingImageFormat.fromContentType(request.contentType());
        validateRequestedFileSize(request.fileSize());

        Meeting meeting =
                meetingRepository
                        .findByIdForUpdate(meetingId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        if (!meeting.getHost().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.MEETING_IMAGE_FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();
        validatePendingUploadLimit(meetingId, now);

        String objectKey =
                properties.meetingObjectPrefix()
                        + "/"
                        + meetingId
                        + "/"
                        + UUID.randomUUID()
                        + "."
                        + format.extension();
        Duration expiration = Duration.ofSeconds(properties.presignedUrlExpirationSeconds());
        meetingImageUploadRepository.save(
                MeetingImageUpload.create(
                        meetingId,
                        userId,
                        objectKey,
                        format.contentType(),
                        request.fileSize(),
                        now.plusSeconds(properties.presignedUrlExpirationSeconds()),
                        now));
        String uploadUrl =
                objectStorage.createPresignedPutUrl(
                        objectKey, format.contentType(), request.fileSize(), expiration);

        return new MeetingImagePresignedUrlResponse(
                uploadUrl,
                objectKey,
                urlResolver.resolve(objectKey),
                properties.presignedUrlExpirationSeconds());
    }

    /**
     * 업로드된 이미지 세트를 모임에 반영한다. objectKeys 에는 이번에 새로 업로드한 key와, 유지할 기존 live key를 섞어 순서대로 담을 수 있다(최대 3장).
     */
    public MeetingImageUpdateResponse updateImages(
            Long meetingId, MeetingImageUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        List<String> keys = request.objectKeys();
        validateKeyList(keys);

        Meeting meeting =
                meetingRepository
                        .findByIdAndDeletedAtIsNull(meetingId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        if (!meeting.getHost().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.MEETING_IMAGE_FORBIDDEN);
        }

        // S3 검증은 DB 락을 오래 잡지 않도록 반영(apply) 이전에 수행한다.
        List<VerifiedMeetingImage> verified = new ArrayList<>();
        for (String key : keys) {
            MeetingImageFormat format = validateAndGetKeyFormat(key, meetingId);
            if (meetingImageRepository.existsByMeetingIdAndObjectKey(meetingId, key)) {
                // 이미 반영된 기존 이미지는 재검증 불필요.
                verified.add(VerifiedMeetingImage.kept(key, format.contentType()));
                continue;
            }
            MeetingImageUpload upload =
                    meetingImageUploadRepository
                            .findByMeetingIdAndObjectKey(meetingId, key)
                            .orElseThrow(
                                    () -> new BusinessException(ErrorCode.INVALID_MEETING_IMAGE_KEY));
            upload.validatePendingSession(LocalDateTime.now(), format.contentType());
            StoredObjectMetadata metadata = objectStorage.getMetadata(key);
            validateStoredObject(metadata, format, upload);
            byte[] content = objectStorage.getContent(key, metadata.eTag());
            if (content.length != metadata.contentLength()) {
                throw new BusinessException(ErrorCode.MEETING_IMAGE_SIZE_MISMATCH);
            }
            imageContentValidator.validate(format, content);
            verified.add(
                    VerifiedMeetingImage.uploaded(key, format.contentType(), metadata.contentLength()));
        }

        meetingImageApplyService.apply(meetingId, userId, verified);

        List<String> urls =
                verified.stream().map(v -> urlResolver.resolve(v.objectKey())).toList();
        return new MeetingImageUpdateResponse(urls);
    }

    private void validateKeyList(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (keys.size() > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.MEETING_IMAGE_COUNT_EXCEEDED);
        }
        if (new HashSet<>(keys).size() != keys.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void validateRequestedFileSize(Long fileSize) {
        if (fileSize == null || fileSize <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (fileSize > properties.maxImageSizeBytes()) {
            throw new BusinessException(ErrorCode.MEETING_IMAGE_SIZE_EXCEEDED);
        }
    }

    private void validatePendingUploadLimit(Long meetingId, LocalDateTime now) {
        long pending =
                meetingImageUploadRepository.countByMeetingIdAndStatusAndExpiresAtAfter(
                        meetingId, MeetingImageUploadStatus.PENDING, now);
        if (pending >= properties.maxPendingUploadsPerUser()) {
            throw new BusinessException(ErrorCode.MEETING_IMAGE_UPLOAD_LIMIT_EXCEEDED);
        }
    }

    private MeetingImageFormat validateAndGetKeyFormat(String objectKey, Long meetingId) {
        if (objectKey == null) {
            throw new BusinessException(ErrorCode.INVALID_MEETING_IMAGE_KEY);
        }
        String expectedPrefix = properties.meetingObjectPrefix() + "/" + meetingId + "/";
        Pattern pattern =
                Pattern.compile(
                        "^"
                                + Pattern.quote(expectedPrefix)
                                + "("
                                + UUID_PATTERN.pattern()
                                + ")\\.(jpg|png|webp)$");
        Matcher matcher = pattern.matcher(objectKey);
        if (!matcher.matches()) {
            throw new BusinessException(ErrorCode.INVALID_MEETING_IMAGE_KEY);
        }
        return MeetingImageFormat.fromExtension(matcher.group(2));
    }

    private void validateStoredObject(
            StoredObjectMetadata metadata, MeetingImageFormat keyFormat, MeetingImageUpload upload) {
        MeetingImageFormat storedFormat = MeetingImageFormat.fromContentType(metadata.contentType());
        if (storedFormat != keyFormat) {
            throw new BusinessException(ErrorCode.INVALID_MEETING_IMAGE_KEY);
        }
        if (metadata.contentLength() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (metadata.contentLength() > properties.maxImageSizeBytes()) {
            throw new BusinessException(ErrorCode.MEETING_IMAGE_SIZE_EXCEEDED);
        }
        if (metadata.contentLength() != upload.getExpectedSize()) {
            throw new BusinessException(ErrorCode.MEETING_IMAGE_SIZE_MISMATCH);
        }
    }
}