package com.gather.gather.domain.auth.entity;

import com.gather.gather.domain.category.entity.Category;
import com.gather.gather.domain.region.entity.Region;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
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

    @Column(nullable = false, length = 12)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, unique = true, length = 8)
    private String nickname;

    @Column(length = 50)
    private String introduction;

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

    @ManyToMany
    @JoinTable(
            name = "user_interest_category",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private List<Category> interestCategories = new ArrayList<>();

    private User(
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
            List<Category> interestCategories) {
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
        this.emailVerified = true;
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
            List<Category> interestCategories) {
        return new User(
                name,
                birthDate,
                gender,
                phoneNumber,
                email,
                password,
                nickname,
                introduction,
                serviceTermsAgreed,
                privacyPolicyAgreed,
                marketingAgreed,
                activityRegion,
                interestCategories);
    }
}
