CREATE TABLE social_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL COMMENT 'SocialProvider enum (KAKAO)',
    provider_user_id VARCHAR(100) NOT NULL COMMENT '제공자가 발급한 회원번호. 카카오는 숫자 문자열.',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 하나의 소셜 계정이 여러 Gather 회원에게 연결되는 것을 막는다(가입 토큰 재사용 방어의 최종 방어선).
    CONSTRAINT uk_social_account_provider_user UNIQUE (provider, provider_user_id),
    -- 한 Gather 회원에 같은 제공자 계정이 여러 개 연결되는 것을 막는다.
    CONSTRAINT uk_social_account_user_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_social_account_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 카카오 회원은 이메일·비밀번호 없이 생성된다. uk_users_email은 MySQL이 NULL 중복을 허용하므로 그대로 유지한다.
ALTER TABLE users
    MODIFY COLUMN email VARCHAR(255) NULL,
    MODIFY COLUMN password VARCHAR(255) NULL;
