-- 회원 탈퇴를 soft delete로 처리하되 익명화는 유예 기간 후에 수행하기 위한 컬럼.
-- 즉시 익명화하면 원 소유자를 식별할 수단이 사라져 "탈퇴 후 7일간 재가입 불가"(PM 결정)를 강제할 수 없다.
-- withdrawn_at 기준으로 스케줄러가 유예 경과분을 익명화한다.
-- 기존 사용자는 활성 사용자로는 모두 NULL이고, 컬럼 추가뿐이므로 롤백 시 DDL을 되돌릴 필요가 없다.
ALTER TABLE users
    ADD COLUMN withdrawn_at      DATETIME(6) NULL,
    ADD COLUMN withdrawal_reason VARCHAR(20) NULL COMMENT 'SELF | KAKAO_UNLINK';
