package com.gather.gather.domain.user.service;

/**
 * 프로필 사진 S3 연동 포트. 실 버킷이 생성되기 전까지는 {@link MockProfileImageStorageClient}가 유일한 구현체다.
 *
 * <p>버킷/IAM Role이 준비되면 {@code S3Presigner} 기반 구현체로 교체한다 — 이 인터페이스와 {@code UserProfileService}의 호출부는
 * 그대로 두고 구현체만 바꾸면 된다(devplan2 2-1-1절, 6절 리스크#7).
 */
public interface ProfileImageStorageClient {

    /**
     * 클라이언트가 이 objectKey로 직접 PUT 업로드할 presigned URL을 발급한다.
     *
     * <p>실패 시 언체크 예외를 던진다 — 호출부(UserProfileService)는 이 실패를 감싸지 않고 그대로 전파해 요청 자체를 실패시킨다.
     */
    ProfileImageUploadUrl createUploadUrl(String objectKey, String contentType);

    /**
     * 프로필 사진 교체로 더는 참조되지 않는 기존 오브젝트를 삭제한다.
     *
     * <p>이 호출은 프로필 수정 성공 이후에 일어나는 뒷정리(best-effort)다 — 실패해도 호출부가 예외를 삼키고 경고 로그만 남기므로, 구현체는 실패 시 예외를
     * 던져도 되지만(로깅을 위해) 그 예외가 프로필 수정 자체를 실패시키지 않는다는 전제로 설계됐다. 이미 없는 키를 삭제하려는 경우도 실 S3 delete가 멱등이므로
     * 예외를 던지지 않는 것을 권장한다.
     */
    void deleteObject(String objectKey);

    /** 화면에 표시할 공개 조회 URL을 조립한다(버킷 정책으로 profiles/* prefix만 공개 GetObject 허용). */
    String buildPublicUrl(String objectKey);

    record ProfileImageUploadUrl(String uploadUrl, long expiresInSeconds) {}
}
