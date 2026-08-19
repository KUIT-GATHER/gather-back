package com.gather.gather.domain.auth.entity;

import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String name;

    @Column private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(nullable = false, unique = true, length = 24)
    private String phoneNumber;

    // 소셜 가입 회원은 이메일·비밀번호를 보유하지 않는다.
    @Column(unique = true, length = 255)
    private String email;

    @Column(length = 255)
    private String password;

    @Column(nullable = false, unique = true, length = 24)
    private String nickname;

    @Column(length = 50)
    private String introduction;

    @Column(name = "profile_image_key", length = 255)
    private String profileImageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column private LocalDateTime withdrawnAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private WithdrawalReason withdrawalReason;

    @Column private LocalDateTime anonymizedAt;

    @Column(nullable = false)
    private boolean emailVerified;

    @Column(nullable = false)
    private boolean serviceTermsAgreed;

    @Column(nullable = false)
    private boolean privacyPolicyAgreed;

    @Column(nullable = false)
    private boolean marketingAgreed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_region_id")
    private Region activityRegion;

    @ElementCollection
    @CollectionTable(name = "user_interest_category", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private List<PostingCategory> interestCategories = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private User(
            String name,
            LocalDate birthDate,
            Gender gender,
            String phoneNumber,
            String email,
            String password,
            String nickname,
            String introduction,
            boolean emailVerified,
            boolean serviceTermsAgreed,
            boolean privacyPolicyAgreed,
            boolean marketingAgreed,
            Region activityRegion,
            List<PostingCategory> interestCategories) {
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.introduction = introduction;
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
        this.emailVerified = emailVerified;
        this.serviceTermsAgreed = serviceTermsAgreed;
        this.privacyPolicyAgreed = privacyPolicyAgreed;
        this.marketingAgreed = marketingAgreed;
        this.activityRegion = activityRegion;
        this.interestCategories = new ArrayList<>(interestCategories);
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static User create(
            String name,
            LocalDate birthDate,
            Gender gender,
            String phoneNumber,
            String email,
            String password,
            String nickname,
            String introduction,
            boolean serviceTermsAgreed,
            boolean privacyPolicyAgreed,
            boolean marketingAgreed,
            Region activityRegion,
            List<PostingCategory> interestCategories) {
        return new User(
                name,
                birthDate,
                gender,
                phoneNumber,
                email,
                password,
                nickname,
                introduction,
                true,
                serviceTermsAgreed,
                privacyPolicyAgreed,
                marketingAgreed,
                activityRegion,
                interestCategories);
    }

    /** 인증할 이메일 자체가 존재하지 않으므로 emailVerified는 false다. 이메일 등록·인증 기능이 생기면 그때 true로 전환한다. */
    public static User createSocial(
            String name,
            LocalDate birthDate,
            Gender gender,
            String phoneNumber,
            String nickname,
            String introduction,
            boolean serviceTermsAgreed,
            boolean privacyPolicyAgreed,
            boolean marketingAgreed,
            Region activityRegion,
            List<PostingCategory> interestCategories) {
        return new User(
                name,
                birthDate,
                gender,
                phoneNumber,
                null,
                null,
                nickname,
                introduction,
                false,
                serviceTermsAgreed,
                privacyPolicyAgreed,
                marketingAgreed,
                activityRegion,
                interestCategories);
    }

    /** 비밀번호 재설정. 이메일 credential이 없는 계정에 password가 새로 생기지 않도록 기존 credential 구조를 요구한다. */
    public void changePassword(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new IllegalArgumentException("암호화된 비밀번호는 필수입니다.");
        }
        if (email == null || password == null) {
            throw new IllegalStateException("이메일 로그인 계정만 비밀번호를 변경할 수 있습니다.");
        }
        this.password = encodedPassword;
        this.updatedAt = LocalDateTime.now();
    }

    public void changeProfileImageKey(String profileImageKey) {
        this.profileImageKey = profileImageKey;
        this.updatedAt = LocalDateTime.now();
    }

    public void requestWithdrawal(WithdrawalReason reason, LocalDateTime now) {
        if (status == UserStatus.WITHDRAWAL_PENDING) {
            return;
        }
        requireTerminationStartStatus();
        requireWithdrawalReason(reason);
        requireWithdrawalTime(now);

        this.status = UserStatus.WITHDRAWAL_PENDING;
        this.withdrawalReason = reason;
        this.updatedAt = now;
    }

    public void withdraw(WithdrawalReason reason, LocalDateTime now) {
        if (isWithdrawn()) {
            return;
        }
        requireTerminationStartStatus();
        requireWithdrawalReason(reason);
        requireWithdrawalTime(now);

        this.status = UserStatus.WITHDRAWN;
        this.withdrawalReason = reason;
        this.withdrawnAt = now;
        this.updatedAt = now;
    }

    public void completePendingWithdrawal(LocalDateTime now) {
        if (isWithdrawn()) {
            return;
        }
        if (status != UserStatus.WITHDRAWAL_PENDING) {
            throw new IllegalStateException("탈퇴 대기 중인 사용자만 비동기 탈퇴를 완료할 수 있습니다.");
        }
        requireWithdrawalReason(withdrawalReason);
        requireWithdrawalTime(now);
        status = UserStatus.WITHDRAWN;
        withdrawnAt = now;
        updatedAt = now;
    }

    public void anonymize(LocalDateTime now) {
        if (isAnonymized()) {
            return;
        }
        if (!isWithdrawn()) {
            throw new IllegalStateException("탈퇴한 사용자만 익명화할 수 있습니다.");
        }
        if (id == null) {
            throw new IllegalStateException("영속화되지 않은 사용자는 익명화할 수 없습니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException("익명화 시각은 필수입니다.");
        }

        this.name = null;
        this.birthDate = null;
        this.gender = null;
        this.phoneNumber = "wdp_" + id;
        this.email = null;
        this.password = null;
        this.nickname = "wdn_" + id;
        this.introduction = null;
        this.profileImageKey = null;
        this.emailVerified = false;
        this.marketingAgreed = false;
        this.activityRegion = null;
        this.interestCategories.clear();
        this.anonymizedAt = now;
        this.updatedAt = now;
    }

    public boolean isWithdrawn() {
        return status == UserStatus.WITHDRAWN;
    }

    public boolean isAnonymized() {
        return anonymizedAt != null;
    }

    private void requireTerminationStartStatus() {
        if (status != UserStatus.ACTIVE && status != UserStatus.SUSPENDED) {
            throw new IllegalStateException("활성 또는 정지 상태의 사용자만 탈퇴를 시작할 수 있습니다.");
        }
    }

    private static void requireWithdrawalReason(WithdrawalReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("탈퇴 사유는 필수입니다.");
        }
    }

    private static void requireWithdrawalTime(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("탈퇴 시각은 필수입니다.");
        }
    }

    /** 마이페이지 프로필 편집. 회원가입과 동일한 필드 집합을 갱신하며, 이메일·전화번호·비밀번호는 이 화면의 편집 대상이 아니다. */
    public void updateProfile(
            String name,
            String nickname,
            String introduction,
            LocalDate birthDate,
            Gender gender,
            Region activityRegion,
            List<PostingCategory> interestCategories) {
        this.name = name;
        this.nickname = nickname;
        this.introduction = introduction;
        this.birthDate = birthDate;
        this.gender = gender;
        this.activityRegion = activityRegion;
        this.interestCategories = new ArrayList<>(interestCategories);
        this.updatedAt = LocalDateTime.now();
    }
}
