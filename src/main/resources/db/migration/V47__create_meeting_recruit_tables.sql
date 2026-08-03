-- 모임 내부 모집공고(RECRUIT 게시글)의 확장 정보와 참여신청.
-- 모집공고는 post(type=RECRUIT) 1건과 1:1로 대응하며, 확장 필드는 post 테이블을 더럽히지 않도록 별도 테이블에 둔다.
-- 참여신청은 외부 봉사공고(posting_participation, V23)와 분리된 모임 내부 전용 테이블로 관리한다.
-- 취소는 물리 삭제로 처리하고 (post_id, user_id) UNIQUE로 중복 신청을 DB 레벨에서 막는다(posting_participation 컨벤션).

CREATE TABLE meeting_recruit (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    post_id           BIGINT       NOT NULL COMMENT 'RECRUIT 유형 post와 1:1',
    place             VARCHAR(255) NOT NULL COMMENT '활동 장소',
    act_date          DATE         NOT NULL COMMENT '활동 날짜',
    act_start_time    TIME         NULL COMMENT '활동 시작 시간',
    act_end_time      TIME         NULL COMMENT '활동 종료 시간',
    max_participants  INT          NOT NULL COMMENT '최대 인원(최대 50)',
    time_recognized   BIT(1)       NOT NULL DEFAULT 0 COMMENT '봉사시간 인정 여부',
    recognized_minutes INT         NULL COMMENT '인정 시간(분). time_recognized=true일 때만 사용',
    apply_deadline    DATE         NOT NULL COMMENT '신청 마감일(오늘~마감일까지 신청 가능)',
    is_external       BIT(1)       NOT NULL DEFAULT 0 COMMENT '외부 공고 공개 여부(현재는 플래그만 저장)',
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_meeting_recruit_post UNIQUE (post_id),
    CONSTRAINT fk_meeting_recruit_post FOREIGN KEY (post_id) REFERENCES post (id)
);

-- 카테고리 1~3개(모임/사용자 관심 카테고리와 동일하게 PostingCategory enum 문자열 저장).
CREATE TABLE meeting_recruit_category (
    recruit_id BIGINT      NOT NULL,
    category   VARCHAR(20) NOT NULL,
    PRIMARY KEY (recruit_id, category),
    CONSTRAINT fk_meeting_recruit_category_recruit
        FOREIGN KEY (recruit_id) REFERENCES meeting_recruit (id) ON DELETE CASCADE
);

CREATE TABLE meeting_recruit_participation (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    post_id    BIGINT      NOT NULL COMMENT '모집공고(RECRUIT post) 참조',
    user_id    BIGINT      NOT NULL COMMENT 'users 테이블 참조',
    status     VARCHAR(20) NOT NULL COMMENT 'APPLIED (현재는 신청/취소만)',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_meeting_recruit_participation_post_user UNIQUE (post_id, user_id),
    CONSTRAINT fk_meeting_recruit_participation_post FOREIGN KEY (post_id) REFERENCES post (id),
    CONSTRAINT fk_meeting_recruit_participation_user FOREIGN KEY (user_id) REFERENCES users (id)
);
