package com.gather.gather.domain.user.service;

/**
 * 프로필 사진 S3 연동 포트. 실 버킷이 생성되기 전까지는 {@link MockProfileImageStorageClient}가 유일한 구현체다.
 *
 * <p>버킷/IAM Role이 준비되면 {@code S3Presigner} 기반 구현체로 교체한다 — 이 인터페이스와 {@code UserProfileService}의 호출부는
 * 그대로 두고 구현체만 바꾸면 된다(devplan2 2-1-1절, 6절 리스크#7).
 */
public interface ProfileImageStorageClient {

    /** 클라이언트가 이 objectKey로 직접 PUT 업로드할 presigned URL을 발급한다. */
    ProfileImageUploadUrl createUploadUrl(String objectKey, String contentType);

    /** 프로필 사진 교체로 더는 참조되지 않는 기존 오브젝트를 삭제한다. */
    void deleteObject(String objectKey);

    /** 화면에 표시할 공개 조회 URL을 조립한다(버킷 정책으로 profiles/* prefix만 공개 GetObject 허용). */
    String buildPublicUrl(String objectKey);

    record ProfileImageUploadUrl(String uploadUrl, long expiresInSeconds) {}
}
