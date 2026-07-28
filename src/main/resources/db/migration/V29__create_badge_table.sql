-- 활동 뱃지 화면(devplan2 8절)용 뱃지 정의 테이블.
-- 달성 기준은 뱃지마다 트리거 도메인이 달라(참여 완료/관심분야/모임 가입·생성/후기 작성) 데이터 기반 범용 기준 컬럼을
-- 두지 않고 code별 전용 판정 로직(BadgeAchievementService)에서 처리한다. 이 테이블은 화면 표시용 메타데이터만 가진다.
-- image_url은 디자인 에셋 전달 전이라 NULL 허용(devplan2 8-4 리스크 #6, 받는 대로 후속 마이그레이션으로 채움).
CREATE TABLE badge (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    code                VARCHAR(30)  NOT NULL,
    name                VARCHAR(50)  NOT NULL,
    description         VARCHAR(255) NOT NULL,
    target_description  VARCHAR(255) NOT NULL,
    image_url           VARCHAR(500),
    display_order       INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_badge_code UNIQUE (code)
);

INSERT INTO badge (code, name, description, target_description, display_order) VALUES
    ('FIRST_VOLUNTEER_COMPLETE', '첫 봉사활동 완료', '첫 봉사활동을 완료했어요', '봉사활동 1회 완료', 1),
    ('INTEREST_CATEGORY_3', '관심분야 3개 선택', '관심 있는 봉사 분야를 3개 이상 선택했어요', '관심분야 3개 이상 선택', 2),
    ('TEAM_JOIN_FIRST', '팀에 처음 가입하기', '처음으로 팀에 가입했어요', '팀 가입 1회', 3),
    ('TEAM_CREATE_FIRST', '팀을 직접 만들기', '처음으로 팀을 직접 만들었어요', '팀 생성 1회', 4),
    ('TEAM_RECRUIT_SUCCESS', '팀원 모집 성공', '내가 만든 팀에 팀원을 모집했어요', '팀원 모집 1명 이상', 5),
    ('VOLUNTEER_5_COMPLETE', '봉사 5회 완료', '봉사활동을 5회 완료했어요', '봉사활동 5회 완료', 6),
    ('REVIEW_WRITE_FIRST', '후기 작성', '처음으로 활동 후기를 작성했어요', '후기 작성 1회', 7),
    ('MONTHLY_2_PARTICIPATION', '월 2회 이상 참여', '한 달 동안 봉사활동에 2회 이상 참여했어요', '한 달 내 봉사활동 2회 완료', 8);
