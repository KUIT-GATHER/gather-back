-- V11: region_group(활동 지역 상위 권역 버튼) 테이블 생성 및 시도 소속 매핑 (2026-07-10)
-- 프론트 회원가입/필터 화면의 9개 버튼(서울/부산/인천/경기/강원/제주/경상/전라/충청) 중
-- 경상/전라/충청은 여러 시도를 묶은 권역이라 1365 행정구역 코드가 존재하지 않아 서비스 내부 코드로 관리한다.
-- 소속 시도가 없는 광역시는 구 관할 기준으로 배정한다: 대구·울산→경상, 광주→전라, 대전·세종→충청.

CREATE TABLE region_group (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    code       VARCHAR(20) NOT NULL COMMENT '서비스 내부 코드(1365 행정구역 코드 아님)',
    name       VARCHAR(20) NOT NULL COMMENT '버튼 표시명',
    sort_order INT         NOT NULL COMMENT '버튼 노출 순서',
    PRIMARY KEY (id),
    CONSTRAINT uk_region_group_code UNIQUE (code)
);

ALTER TABLE region
    ADD COLUMN region_group_id BIGINT NULL COMMENT '소속 권역(시도 레벨 행에만 설정)';

ALTER TABLE region
    ADD CONSTRAINT fk_region_region_group FOREIGN KEY (region_group_id) REFERENCES region_group (id);

INSERT INTO region_group (code, name, sort_order) VALUES
    ('GRP_SEOUL', '서울', 1),
    ('GRP_BUSAN', '부산', 2),
    ('GRP_INCHEON', '인천', 3),
    ('GRP_GYEONGGI', '경기', 4),
    ('GRP_GANGWON', '강원', 5),
    ('GRP_JEJU', '제주', 6),
    ('GRP_GYEONGSANG', '경상', 7),
    ('GRP_JEOLLA', '전라', 8),
    ('GRP_CHUNGCHEONG', '충청', 9);

-- 단일 시도 = 해당 버튼과 1:1
UPDATE region r JOIN region_group g ON g.code = 'GRP_SEOUL'
    SET r.region_group_id = g.id WHERE r.code = '6110000';
UPDATE region r JOIN region_group g ON g.code = 'GRP_BUSAN'
    SET r.region_group_id = g.id WHERE r.code = '6260000';
UPDATE region r JOIN region_group g ON g.code = 'GRP_INCHEON'
    SET r.region_group_id = g.id WHERE r.code = '6280000';
UPDATE region r JOIN region_group g ON g.code = 'GRP_GYEONGGI'
    SET r.region_group_id = g.id WHERE r.code = '6410000';
UPDATE region r JOIN region_group g ON g.code = 'GRP_GANGWON'
    SET r.region_group_id = g.id WHERE r.code = '6420000';
UPDATE region r JOIN region_group g ON g.code = 'GRP_JEJU'
    SET r.region_group_id = g.id WHERE r.code = '6500000';

-- 광역권 = 여러 시도 묶음
UPDATE region r JOIN region_group g ON g.code = 'GRP_GYEONGSANG'
    SET r.region_group_id = g.id WHERE r.code IN ('6270000', '6310000', '6470000', '6480000');
UPDATE region r JOIN region_group g ON g.code = 'GRP_JEOLLA'
    SET r.region_group_id = g.id WHERE r.code IN ('6290000', '6450000', '6460000');
UPDATE region r JOIN region_group g ON g.code = 'GRP_CHUNGCHEONG'
    SET r.region_group_id = g.id WHERE r.code IN ('5690000', '6300000', '6430000', '6440000');
