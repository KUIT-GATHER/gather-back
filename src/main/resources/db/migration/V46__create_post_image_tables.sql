-- 게시글 이미지(최대 3장). 프로필/모임 이미지와 동일하게 presigned S3 업로드를 쓴다.
-- 게시글은 "작성 화면에서 먼저 이미지를 올리고, 등록 시 objectKey를 넘기는" 흐름이라 업로드 세션은
-- 특정 post에 묶이지 않고 사용자(user_id) 단위로 발급한다(profile_image_upload와 동일한 형태).
--
-- post_image      : 게시글에 실제 반영된 이미지(노출 순서 sort_order).
-- post_image_upload: presigned URL 발급 후 아직 게시글에 반영되지 않은 업로드 세션(PENDING/APPLIED).
CREATE TABLE post_image (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    post_id    BIGINT       NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    sort_order INT          NOT NULL COMMENT '게시글 내 이미지 노출 순서(0부터)',
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_post_image_post FOREIGN KEY (post_id) REFERENCES post (id),
    CONSTRAINT uk_post_image_post_sort UNIQUE (post_id, sort_order),
    CONSTRAINT uk_post_image_object_key UNIQUE (object_key)
);

CREATE INDEX idx_post_image_post ON post_image (post_id, sort_order);

CREATE TABLE post_image_upload (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL COMMENT 'users 테이블 참조',
    object_key    VARCHAR(512) NOT NULL,
    content_type  VARCHAR(50)  NOT NULL,
    expected_size BIGINT       NOT NULL,
    status        VARCHAR(20)  NOT NULL COMMENT 'PENDING / APPLIED',
    expires_at    DATETIME(6)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    applied_at    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_post_image_upload_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_post_image_upload_object_key UNIQUE (object_key)
);

-- 사용자별 미반영(PENDING) 발급 한도 카운트(status + expires_at) 커버용.
CREATE INDEX idx_post_image_upload_user_status ON post_image_upload (user_id, status, expires_at);
