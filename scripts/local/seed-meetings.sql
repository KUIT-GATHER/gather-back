-- 로컬 개발 DB 전용 모임 시드 데이터
-- Flyway가 자동 실행하지 않는다. scripts/local/README.md의 안내에 따라 직접 실행한다.

START TRANSACTION;

SET @seed_host_id = (
    SELECT id
    FROM users
    WHERE status = 'ACTIVE'
    ORDER BY id
    LIMIT 1
);

SET @seed_region_id = (
    SELECT id
    FROM region
    ORDER BY id
    LIMIT 1
);

INSERT INTO meeting (
    name,
    description,
    max_member,
    current_member_count,
    deadline,
    memo,
    region_id,
    host_id,
    volunteer_posting_id,
    participation_condition,
    status,
    activity_start_at,
    activity_end_at,
    created_at,
    updated_at,
    deleted_at
)
SELECT
    seed.name,
    seed.description,
    seed.max_member,
    1,
    DATE_ADD(NOW(), INTERVAL seed.deadline_days DAY),
    seed.memo,
    @seed_region_id,
    @seed_host_id,
    NULL,
    seed.participation_condition,
    'RECRUITING',
    DATE_ADD(NOW(), INTERVAL seed.start_days DAY),
    DATE_ADD(DATE_ADD(NOW(), INTERVAL seed.start_days DAY), INTERVAL 3 HOUR),
    NOW(),
    NOW(),
    NULL
FROM (
    SELECT
        '[로컬] 한강공원 플로깅팀' AS name,
        '한강공원에서 함께 쓰레기를 줍는 환경 봉사 모임입니다.' AS description,
        12 AS max_member,
        10 AS deadline_days,
        '편한 복장과 장갑을 준비해 주세요.' AS memo,
        14 AS start_days,
        '누구나 참여할 수 있습니다.' AS participation_condition
    UNION ALL
    SELECT
        '[로컬] 주말 유기견 산책 봉사',
        '보호소 유기견과 산책하며 교감하는 모임입니다.',
        8,
        7,
        '운동화를 착용해 주세요.',
        11,
        '동물을 사랑하는 성인'
    UNION ALL
    SELECT
        '[로컬] 어린이 독서 멘토링',
        '지역 아동센터에서 어린이와 함께 책을 읽습니다.',
        10,
        15,
        '선정 도서는 모임 공지에서 안내합니다.',
        20,
        '월 1회 이상 참여 가능한 분'
    UNION ALL
    SELECT
        '[로컬] 벽화 정비 봉사단',
        '노후 벽화를 정비하고 지역 골목을 꾸미는 모임입니다.',
        15,
        18,
        '작업복과 여벌 옷을 준비해 주세요.',
        24,
        '미술 경험이 없어도 참여할 수 있습니다.'
    UNION ALL
    SELECT
        '[로컬] 어르신 디지털 도우미',
        '스마트폰 사용이 어려운 어르신을 돕는 모임입니다.',
        6,
        5,
        '개인 스마트폰을 지참해 주세요.',
        9,
        '스마트폰 기본 기능을 설명할 수 있는 분'
) seed
WHERE @seed_host_id IS NOT NULL
  AND @seed_region_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM meeting existing
      WHERE existing.name = seed.name
        AND existing.deleted_at IS NULL
  );

INSERT INTO meeting_category (meeting_id, category)
SELECT
    meeting.id,
    seed_category.category
FROM meeting
JOIN (
    SELECT '[로컬] 한강공원 플로깅팀' AS meeting_name, 'ENVIRONMENT' AS category
    UNION ALL
    SELECT '[로컬] 한강공원 플로깅팀', 'COMMUNITY'
    UNION ALL
    SELECT '[로컬] 주말 유기견 산책 봉사', 'WELFARE'
    UNION ALL
    SELECT '[로컬] 어린이 독서 멘토링', 'EDUCATION'
    UNION ALL
    SELECT '[로컬] 어린이 독서 멘토링', 'COMMUNITY'
    UNION ALL
    SELECT '[로컬] 벽화 정비 봉사단', 'CULTURE'
    UNION ALL
    SELECT '[로컬] 어르신 디지털 도우미', 'COMMUNITY'
    UNION ALL
    SELECT '[로컬] 어르신 디지털 도우미', 'EDUCATION'
) seed_category ON seed_category.meeting_name = meeting.name
WHERE meeting.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM meeting_category existing_category
      WHERE existing_category.meeting_id = meeting.id
        AND existing_category.category = seed_category.category
  );

INSERT INTO meeting_member (
    user_id,
    meeting_id,
    role,
    status,
    joined_at,
    created_at,
    updated_at
)
SELECT
    @seed_host_id,
    meeting.id,
    'HOST',
    'APPROVED',
    NOW(),
    NOW(),
    NOW()
FROM meeting
WHERE meeting.name IN (
    '[로컬] 한강공원 플로깅팀',
    '[로컬] 주말 유기견 산책 봉사',
    '[로컬] 어린이 독서 멘토링',
    '[로컬] 벽화 정비 봉사단',
    '[로컬] 어르신 디지털 도우미'
)
  AND meeting.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM meeting_member member
      WHERE member.user_id = @seed_host_id
        AND member.meeting_id = meeting.id
  );

COMMIT;
