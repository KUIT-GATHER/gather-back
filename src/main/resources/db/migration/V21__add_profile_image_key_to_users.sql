-- 마이페이지 프로필 사진 실 업로드(S3 presigned URL) 기능용.
-- 전체 URL이 아니라 S3 오브젝트 키(profiles/{userId}/{uuid}.{ext})만 저장한다 — 버킷/CDN 도메인이
-- 바뀌어도 백필 없이 응답 시점에 URL을 조립할 수 있다(동현 IAM Role 확정 회신, 2026-07-20).
ALTER TABLE users
    ADD COLUMN profile_image_key VARCHAR(255) NULL;
