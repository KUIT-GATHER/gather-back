package com.gather.gather.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "account_identity_guard",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_account_identity_guard_identity",
                        columnNames = {"identity_type", "key_version", "identity_hash"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountIdentityGuard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountRejoinBlockIdentifierType identityType;

    @Column(nullable = false, length = 64)
    private String identityHash;

    @Column(nullable = false)
    private int keyVersion;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
