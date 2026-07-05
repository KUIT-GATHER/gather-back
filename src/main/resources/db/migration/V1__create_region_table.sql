CREATE TABLE region (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    name      VARCHAR(255) NOT NULL COMMENT '지역명(행정구역명)',
    level     INT          NULL COMMENT '1=도, 2=시, 3=구, 4=동',
    code      VARCHAR(255) NULL COMMENT '1365 행정구역 코드 매핑용',
    parent_id BIGINT       NULL COMMENT '도→시→구→동 셀프참조 (최상위 지역은 null)',
    PRIMARY KEY (id),
    CONSTRAINT uk_region_code UNIQUE (code),
    CONSTRAINT fk_region_parent FOREIGN KEY (parent_id) REFERENCES region (id)
);
