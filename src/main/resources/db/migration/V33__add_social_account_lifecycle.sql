-- 운영 social_account 데이터 유무를 확인하지 못했으므로 기존 평문 컬럼을 유지한 채 단계적으로 전환한다.
ALTER TABLE social_account
    ADD COLUMN provider_user_key VARCHAR(64) NULL
        COMMENT 'RejoinBlockIdentifierHasher.hashKakao 결과의 hash',
    ADD COLUMN provider_user_key_version INT NULL
        COMMENT '카카오 조회 HMAC 키 버전',
    ADD COLUMN provider_user_id_ciphertext VARCHAR(512) NULL
        COMMENT 'Kakao Admin unlink 호출용 AES-GCM 암호문',
    ADD COLUMN encryption_key_version INT NULL
        COMMENT '카카오 회원번호 암호화 키 버전',
    ADD COLUMN link_status VARCHAR(20) NULL
        COMMENT 'SocialAccountLinkStatus enum',
    ADD COLUMN generation BIGINT NULL
        COMMENT '동일 카카오 계정의 Gather User 연결 세대',
    ADD COLUMN connected_at DATETIME(6) NULL,
    ADD COLUMN unlinked_at DATETIME(6) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0
        COMMENT 'SocialAccount 상태 전이와 generation 변경의 낙관적 잠금 버전',
    ADD CONSTRAINT uk_social_account_provider_key
        UNIQUE (provider, provider_user_key);

-- 기존 uk_social_account_provider_user와 provider_user_id는 backfill 검증 및 후속 migration까지 유지한다.
