package com.gather.gather.global.infra.s3;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Duration;

/** 프로필 이미지 객체 저장소 계약. */
public interface ObjectStorage {

    /** 조건부 생성용 PUT URL을 발급한다. 동일 key의 재업로드는 저장소가 거절해야 한다. */
    String createPresignedPutUrl(
            String objectKey, String contentType, long contentLength, Duration expiration);

    /**
     * 객체 메타데이터를 조회한다.
     *
     * @throws BusinessException {@link ErrorCode#PROFILE_IMAGE_OBJECT_NOT_FOUND} 객체가 없을 때
     * @throws BusinessException {@link ErrorCode#S3_OPERATION_FAILED} 저장소 통신이 실패했을 때
     */
    StoredObjectMetadata getMetadata(String objectKey);

    /**
     * 조회한 객체 버전이 {@code expectedVersion}과 일치할 때만 내용을 읽는다.
     *
     * @throws BusinessException {@link ErrorCode#PROFILE_IMAGE_OBJECT_NOT_FOUND} 객체가 없을 때
     * @throws BusinessException {@link ErrorCode#PROFILE_IMAGE_UPLOAD_CONFLICT} 조회 시점에 객체 버전이 바뀌었을
     *     때
     * @throws BusinessException {@link ErrorCode#S3_OPERATION_FAILED} 저장소 통신이 실패했을 때
     */
    byte[] getContent(String objectKey, String expectedVersion);

    /** 객체를 삭제한다. 이미 없는 객체는 성공으로 처리한다. */
    void delete(String objectKey);
}
