package com.gather.gather.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 비밀번호 재설정 권한. 행의 존재 자체가 사용 가능한 재설정 credential 후보이며, 사용·재발급·만료 시 물리 삭제한다.
 *
 * <p>raw token은 저장하지 않고 SHA-256 hash만 보관한다.
 */
@Entity
@Getter
@Table(name = "password_reset_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    private static final Pattern TOKEN_HASH_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private PasswordResetToken(
            User user, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt) {
        if (user == null) {
            throw new IllegalArgumentException("비밀번호 재설정 토큰의 사용자는 필수입니다.");
        }
        if (tokenHash == null || !TOKEN_HASH_PATTERN.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException("비밀번호 재설정 토큰 hash는 64자 소문자 hex여야 합니다.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("비밀번호 재설정 토큰 생성 시각은 필수입니다.");
        }
        if (expiresAt == null || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("비밀번호 재설정 토큰 만료 시각은 생성 시각보다 늦어야 합니다.");
        }
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static PasswordResetToken issue(
            User user, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt) {
        return new PasswordResetToken(user, tokenHash, expiresAt, createdAt);
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }
}
