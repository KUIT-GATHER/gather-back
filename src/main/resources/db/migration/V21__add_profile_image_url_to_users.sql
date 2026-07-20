-- 마이페이지 프로필 사진 실 업로드(S3 presigned URL) 기능용. 업로드 완료 후 최종 S3 오브젝트 URL을 저장한다.
ALTER TABLE users
    ADD COLUMN profile_image_url VARCHAR(500) NULL;
