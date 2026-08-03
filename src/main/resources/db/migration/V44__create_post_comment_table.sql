-- 모임 게시글 댓글. 대댓글(계층)은 두지 않는 플랫 구조이며 deletedAt으로 소프트 삭제한다(post 도메인 컨벤션).
-- 좋아요/댓글수는 post 테이블의 집계 컬럼(comment_count)으로 관리하고, 여기서는 원본 행만 보관한다.
-- V9 이후 users는 Flyway 관리 대상이고 post(V12)도 users(id)/meeting(id) FK를 걸므로 그 컨벤션을 따른다.
-- 회원 탈퇴는 users.status를 WITHDRAWN으로 바꾸는 소프트 삭제라 물리 삭제가 없으므로 ON DELETE는 기본 RESTRICT를 둔다.
CREATE TABLE post_comment (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    post_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL COMMENT 'users 테이블 참조',
    content    VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_post_comment_post FOREIGN KEY (post_id) REFERENCES post (id),
    CONSTRAINT fk_post_comment_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 게시글 상세의 댓글 목록 조회(post_id + 미삭제 + 오래된 순) 커버용.
CREATE INDEX idx_post_comment_post_created ON post_comment (post_id, deleted_at, created_at, id);
