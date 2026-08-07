package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.meeting.dto.MeetingImageListResponse;
import com.gather.gather.domain.meeting.dto.MeetingImagePresignedUrlRequest;
import com.gather.gather.domain.meeting.dto.MeetingImagePresignedUrlResponse;
import com.gather.gather.domain.meeting.dto.MeetingImageUpdateRequest;
import com.gather.gather.domain.meeting.dto.MeetingImageUpdateResponse;
import com.gather.gather.domain.meeting.dto.MeetingManageImageResponse;
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
        // Medium: 상세 조회와 동일하게 소프트딜리트된 모임은 404 처리.
        if (meetingRepository.findByIdAndDeletedAtIsNull(meetingId).isEmpty()) {
            throw new BusinessException(ErrorCode.MEETING_NOT_FOUND);
        }
        List<String> urls =
                meetingImageRepository.findByMeetingIdOrderBySortOrderAsc(meetingId).stream()
                        .map(MeetingImage::getObjectKey)
                        .map(urlResolver::resolve)
                        .toList();
        return new MeetingImageListResponse(urls);
    }

    /** 이미지 수정 화면용 - objectKey를 포함해 반환한다(모임장 전용). 재정렬·부분 유지 시 objectKey가 필요하다. */
    @Transactional(readOnly = true)
    public List<MeetingManageImageResponse> getManageImages(Long meetingId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Meeting meeting =
                meetingRepository
                        .findByIdAndDeletedAtIsNull(meetingId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        if (!meeting.getHost().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.MEETING_IMAGE_FORBIDDEN);
        }
        return meetingImageRepository.findByMeetingIdOrderBySortOrderAsc(meetingId).stream()
                .map(
                        image ->
                                new MeetingManageImageResponse(
                                        image.getObjectKey(),
                                        urlResolver.resolve(image.getObjectKey()),
                                        image.getSortOrder()))
                .toList();
    }

    @Transactional
    public MeetingImagePresignedUrlResponse createPresignedUrl(
            Long meetingId, MeetingImagePresignedUrlRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        MeetingImageFormat format = MeetingImageFormat.fromContentType(request.contentType());
        validateRequestedFileSize(request.fileSize());

        Meeting meeting =
                meetingRepository
                        .findByIdAndDeletedAtIsNullForUpdate(meetingId)
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
                verified.add(VerifiedMeetingImage.kept(key, format.contentType()));
                continue;
            }
            MeetingImageUpload upload =
                    meetingImageUploadRepository
                            .findByMeetingIdAndObjectKey(meetingId, key)
                            .orElseThrow(
                                    () ->
                                            new BusinessException(
                                                    ErrorCode.INVALID_MEETING_IMAGE_KEY));
            upload.validatePendingSession(LocalDateTime.now(), format.contentType());

            StoredObjectMetadata metadata;
            byte[] content;
            try {
                metadata = objectStorage.getMetadata(key);
                validateStoredObject(metadata, format, upload);
                content = objectStorage.getContent(key, metadata.eTag());
            } catch (BusinessException e) {
                // Medium: 공용 S3 계층의 profile 전용 오류 코드를 모임 이미지 계약으로 변환.
                throw new BusinessException(mapStorageError(e.getErrorCode()));
            }
            if (content.length != metadata.contentLength()) {
                throw new BusinessException(ErrorCode.MEETING_IMAGE_SIZE_MISMATCH);
            }
            imageContentValidator.validate(format, content);
            verified.add(
                    VerifiedMeetingImage.uploaded(
                            key, format.contentType(), metadata.contentLength()));
        }

        meetingImageApplyService.apply(meetingId, userId, verified);

        List<String> urls = verified.stream().map(v -> urlResolver.resolve(v.objectKey())).toList();
        return new MeetingImageUpdateResponse(urls);
    }

    // ⚠️ 아래 두 case의 문자열은 실제 ErrorCode의 profile 전용 코드명과 일치해야 한다(오타 시 매핑만 안 될 뿐 컴파일은 됨).
    private ErrorCode mapStorageError(ErrorCode code) {
        return switch (code.name()) {
            case "PROFILE_IMAGE_OBJECT_NOT_FOUND" -> ErrorCode.MEETING_IMAGE_OBJECT_NOT_FOUND;
            case "PROFILE_IMAGE_UPLOAD_CONFLICT" -> ErrorCode.MEETING_IMAGE_UPLOAD_CONFLICT;
            default -> code;
        };
    }

    private void validateKeyList(List<String> keys) {
        // 빈 배열은 전체 이미지 삭제 요청으로 허용한다(정책). null만 막는다.
        if (keys == null) {
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
            StoredObjectMetadata metadata,
            MeetingImageFormat keyFormat,
            MeetingImageUpload upload) {
        MeetingImageFormat storedFormat =
                MeetingImageFormat.fromContentType(metadata.contentType());
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
