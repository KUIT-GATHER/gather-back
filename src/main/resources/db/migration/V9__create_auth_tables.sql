CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(12) NOT NULL,
    birth_date DATE NOT NULL,
    gender ENUM ('FEMALE', 'MALE') NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(8) NOT NULL,
    introduction VARCHAR(50) NULL,
    role ENUM ('ADMIN', 'USER') NOT NULL,
    status ENUM ('ACTIVE', 'SUSPENDED', 'WITHDRAWN') NOT NULL,
    email_verified BIT(1) NOT NULL,
    service_terms_agreed BIT(1) NOT NULL,
    privacy_policy_agreed BIT(1) NOT NULL,
    marketing_agreed BIT(1) NOT NULL,
    activity_region_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_phone_number UNIQUE (phone_number),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_nickname UNIQUE (nickname),
    CONSTRAINT fk_users_activity_region FOREIGN KEY (activity_region_id) REFERENCES region (id)
);

CREATE TABLE email_verification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    code VARCHAR(10) NOT NULL,
    verified BIT(1) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    verified_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_email_verification_email UNIQUE (email)
);

CREATE TABLE refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE user_interest_category (
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, category_id),
    CONSTRAINT fk_user_interest_category_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_interest_category_category FOREIGN KEY (category_id) REFERENCES categories (id)
);
