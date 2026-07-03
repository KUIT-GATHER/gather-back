package com.gather.gather.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "email_verification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime verifiedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private EmailVerification(String email, String code, LocalDateTime expiresAt) {
        this.email = email;
        this.code = code;
        this.verified = false;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public static EmailVerification create(String email, String code, LocalDateTime expiresAt) {
        return new EmailVerification(email, code, expiresAt);
    }

    public void refresh(String code, LocalDateTime expiresAt) {
        this.code = code;
        this.expiresAt = expiresAt;
        this.verified = false;
        this.verifiedAt = null;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public void verify(LocalDateTime verifiedAt) {
        this.verified = true;
        this.verifiedAt = verifiedAt;
    }
}
