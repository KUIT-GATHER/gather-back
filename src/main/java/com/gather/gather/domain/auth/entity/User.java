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

    @Column(nullable = false, length = 20)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(nullable = false, unique = true, length = 20)
    private String phoneNumber;

    // 소셜 가입 회원은 이메일·비밀번호를 보유하지 않는다.
    @Column(unique = true, length = 255)
    private String email;

    @Column(length = 255)
    private String password;

    @Column(nullable = false, unique = true, length = 20)
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

    @Column(nullable = false)
    private boolean emailVerified;

    @Column(nullable = false)
    private boolean serviceTermsAgreed;

    @Column(nullable = false)
    private boolean privacyPolicyAgreed;

    @Column(nullable = false)
    private boolean marketingAgreed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_region_id", nullable = false)
    private Region activityRegion;

    @ElementCollection
    @CollectionTable(name = "user_interest_category", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private List<PostingCategory> interestCategories = new ArrayList<>();

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

    public void changeProfileImageKey(String profileImageKey) {
        this.profileImageKey = profileImageKey;
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
    }
}
