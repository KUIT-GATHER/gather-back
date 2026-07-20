package com.gather.gather.domain.user.service;

import com.gather.gather.domain.user.config.ProfileImageStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S3 버킷이 아직 생성되지 않아 실제 presigned URL을 발급할 수 없는 동안 쓰는 임시 구현체(devplan2 6절 리스크#7).
 *
 * <p>{@link #createUploadUrl}이 반환하는 URL은 실제로 업로드가 되지 않는 가짜 값이다 — 프론트가 응답 계약(uploadUrl/objectKey
 * 필드)에 맞춰 미리 연동할 수 있게 하는 것이 목적이며, 실 버킷이 생기면 이 클래스를 {@code S3Presigner} 기반 구현체로 교체한다.
 *
 * <p>{@link #buildPublicUrl}은 가짜가 아니다 — S3 공개 URL 포맷을 그대로 조립하므로 버킷이 실제로 생기고 오브젝트가 존재하면 그대로 동작한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockProfileImageStorageClient implements ProfileImageStorageClient {

    private static final long MOCK_EXPIRES_IN_SECONDS = 300;

    private final ProfileImageStorageProperties properties;

    @Override
    public ProfileImageUploadUrl createUploadUrl(String objectKey, String contentType) {
        String mockUploadUrl =
                buildPublicUrl(objectKey) + "?mock-presigned=true&contentType=" + contentType;
        return new ProfileImageUploadUrl(mockUploadUrl, MOCK_EXPIRES_IN_SECONDS);
    }

    @Override
    public void deleteObject(String objectKey) {
        log.info("[MockProfileImageStorageClient] 실 S3 연동 전이라 삭제를 건너뜁니다. objectKey={}", objectKey);
    }

    @Override
    public String buildPublicUrl(String objectKey) {
        return "https://"
                + properties.bucket()
                + ".s3."
                + properties.region()
                + ".amazonaws.com/"
                + objectKey;
    }
}
