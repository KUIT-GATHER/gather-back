-- 게시글 좋아요. 취소는 물리 삭제로 처리하고(재좋아요 시 새 행 INSERT), (post_id, user_id) UNIQUE로
-- 동시 중복 좋아요를 DB 레벨에서 막는다(posting_participation(V23)의 취소=물리삭제 컨벤션과 동일).
-- 좋아요 총계는 post.like_count 집계 컬럼으로 노출하며, 이 테이블은 "누가 눌렀는지"만 보관한다.
CREATE TABLE post_like (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    post_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL COMMENT 'users 테이블 참조',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_post_like_post FOREIGN KEY (post_id) REFERENCES post (id),
    CONSTRAINT fk_post_like_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_post_like_post_user UNIQUE (post_id, user_id)
);
