package com.gather.gather.domain.post.service;

import com.gather.gather.domain.post.dto.PostImagePresignedUrlRequest;
import com.gather.gather.domain.post.dto.PostImagePresignedUrlResponse;
import com.gather.gather.domain.post.entity.PostImage;
import com.gather.gather.domain.post.entity.PostImageUpload;
import com.gather.gather.domain.post.entity.PostImageUploadStatus;
import com.gather.gather.domain.post.enums.PostImageFormat;
import com.gather.gather.domain.post.repository.PostImageRepository;
import com.gather.gather.domain.post.repository.PostImageUploadRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.infra.s3.ObjectStorage;
import com.gather.gather.global.infra.s3.S3Properties;
import com.gather.gather.global.util.SecurityUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 이미지 presigned 업로드 및 반영.
 *
 * <p>프로필/모임 이미지와 동일하게 presigned PUT URL로 프론트가 S3에 직접 업로드하고, 게시글 작성/수정 시 objectKey 목록을 넘겨
 * 반영한다. 반영 시 업로드 세션(PENDING)을 검증해 APPLIED로 전환하며 {@code post_image}에 순서대로 저장한다.
 *
 * <p>주의: 모임 이미지와 달리 반영 단계에서 S3 객체 바이트 재검증(다운로드/포맷 확인)은 하지 않는다(스코프 축소). 필요 시 {@code
 * MeetingImageService}처럼 강화할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostImageService {

    /** 게시글당 최대 이미지 수(피그마 기준). */
    public static final int MAX_IMAGES_PER_POST = 3;

    /** 사용자별 미반영 발급 세션 상한(과도한 발급 방지). */
    private static final int MAX_PENDING_UPLOADS_PER_USER = 10;

    private static final String OBJECT_PREFIX = "posts";

    private final PostImageRepository postImageRepository;
    private final PostImageUploadRepository postImageUploadRepository;
    private final ObjectStorage objectStorage;
    private final S3Properties properties;

    @Transactional
    public PostImagePresignedUrlResponse createPresignedUrl(PostImagePresignedUrlRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        PostImageFormat format = PostImageFormat.fromContentType(request.contentType());
        validateFileSize(request.fileSize());

        LocalDateTime now = LocalDateTime.now();
        validatePendingUploadLimit(userId, now);

        String objectKey =
                OBJECT_PREFIX + "/" + userId + "/" + UUID.randomUUID() + "." + format.extension();
        long expirationSeconds = properties.presignedUrlExpirationSeconds();
        postImageUploadRepository.save(
                PostImageUpload.create(
                        userId,
                        objectKey,
                        format.contentType(),
                        request.fileSize(),
                        now.plusSeconds(expirationSeconds),
                        now));
        String uploadUrl =
                objectStorage.createPresignedPutUrl(
                        objectKey,
                        format.contentType(),
                        request.fileSize(),
                        Duration.ofSeconds(expirationSeconds));

        return new PostImagePresignedUrlResponse(
                uploadUrl, objectKey, resolve(objectKey), expirationSeconds);
    }

    /**
     * 게시글 이미지 세트를 반영한다(작성·수정 공용). {@code objectKeys}가 null이면 변경 없음, 빈 리스트면 전체 제거. 순서가 노출 순서가 되며,
     * 이미 반영된 키는 유지하고 신규 키는 발급 세션(PENDING)을 검증해 반영한다.
     */
    @Transactional
    public void setImages(Long userId, Long postId, List<String> objectKeys) {
        if (objectKeys == null) {
            return;
        }
        if (objectKeys.size() > MAX_IMAGES_PER_POST) {
            throw new BusinessException(ErrorCode.POST_IMAGE_COUNT_EXCEEDED);
        }
        if (new HashSet<>(objectKeys).size() != objectKeys.size()) {
            throw new BusinessException(ErrorCode.INVALID_POST_IMAGE_KEY);
        }

        Set<String> existingKeys = new HashSet<>();
        for (PostImage image : postImageRepository.findByPostIdOrderBySortOrderAsc(postId)) {
            existingKeys.add(image.getObjectKey());
        }

        // 순서 재배치 시 (post_id, sort_order) UNIQUE 충돌을 피하려고 전량 삭제 후 재삽입한다.
        postImageRepository.deleteByPostId(postId);
        postImageRepository.flush();

        LocalDateTime now = LocalDateTime.now();
        int order = 0;
        for (String key : objectKeys) {
            if (!existingKeys.contains(key)) {
                PostImageUpload upload =
                        postImageUploadRepository
                                .findByUserIdAndObjectKey(userId, key)
                                .orElseThrow(
                                        () ->
                                                new BusinessException(
                                                        ErrorCode.INVALID_POST_IMAGE_KEY));
                if (!upload.isPending()) {
                    throw new BusinessException(ErrorCode.INVALID_POST_IMAGE_KEY);
                }
                if (upload.isExpired(now)) {
                    throw new BusinessException(ErrorCode.POST_IMAGE_UPLOAD_EXPIRED);
                }
                upload.apply(now);
            }
            postImageRepository.save(PostImage.create(postId, key, order));
            order++;
        }
    }

    /** 단일 게시글의 이미지 공개 URL 목록(노출 순서). */
    public List<String> resolveUrls(Long postId) {
        return postImageRepository.findByPostIdOrderBySortOrderAsc(postId).stream()
                .map(image -> resolve(image.getObjectKey()))
                .toList();
    }

    /** 목록 화면용: 여러 게시글의 이미지 URL을 postId별로 묶어 반환한다. */
    public Map<Long, List<String>> resolveUrlsByPostIds(Collection<Long> postIds) {
        Map<Long, List<String>> result = new HashMap<>();
        if (postIds.isEmpty()) {
            return result;
        }
        postImageRepository.findByPostIdInOrderByPostIdAscSortOrderAsc(postIds)
                .forEach(
                        image ->
                                result.computeIfAbsent(image.getPostId(), key -> new ArrayList<>())
                                        .add(resolve(image.getObjectKey())));
        return result;
    }

    private String resolve(String objectKey) {
        return properties.publicBaseUrl() + "/" + objectKey;
    }

    private void validateFileSize(long fileSize) {
        if (fileSize > properties.maxImageSizeBytes()) {
            throw new BusinessException(ErrorCode.POST_IMAGE_SIZE_EXCEEDED);
        }
    }

    private void validatePendingUploadLimit(Long userId, LocalDateTime now) {
        long pending =
                postImageUploadRepository.countByUserIdAndStatusAndExpiresAtAfter(
                        userId, PostImageUploadStatus.PENDING, now);
        if (pending >= MAX_PENDING_UPLOADS_PER_USER) {
            throw new BusinessException(ErrorCode.POST_IMAGE_UPLOAD_LIMIT_EXCEEDED);
        }
    }
}
