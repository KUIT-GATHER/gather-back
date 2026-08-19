-- 이메일 인증 코드를 평문 대신 HMAC-SHA256으로 저장하기 위해 code_hash를 추가한다.
-- 구 버전 JAR로 롤백되어도 스키마가 맞아야 하므로 기존 code 컬럼은 제거하지 않고,
-- 롤백된 구 버전이 code_hash 없이 INSERT할 수 있도록 NULL을 허용한다.
-- 기존 평문 행 삭제는 마이그레이션에서 하지 않고 애플리케이션 기동 시점의 purge가 담당한다.
ALTER TABLE email_verification
    ADD COLUMN code_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER code;
