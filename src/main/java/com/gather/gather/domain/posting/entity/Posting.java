package com.gather.gather.domain.posting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "volunteer_posting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Posting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ext_id", unique = true)
    private String extId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostingStatus status;

    @Lob private String content;

    @Column(name = "recruit_org")
    private String recruitOrg;

    @Column(name = "register_org")
    private String registerOrg;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "act_start_date")
    private LocalDate actStartDate;

    @Column(name = "act_end_date")
    private LocalDate actEndDate;

    @Column(name = "act_start_time")
    private String actStartTime;

    @Column(name = "act_end_time")
    private String actEndTime;

    @Column(name = "notice_start_date")
    private LocalDate noticeStartDate;

    @Column(name = "notice_end_date")
    private LocalDate noticeEndDate;

    @Column(name = "act_wkdy")
    private String actWkdy;

    @Column(name = "recruit_count")
    private Integer recruitCount;

    @Column(name = "applicant_count")
    private Integer applicantCount;

    @Column(name = "is_adult")
    private Boolean isAdult;

    @Column(name = "is_teen")
    private Boolean isTeen;

    @Column(name = "is_group")
    private Boolean isGroup;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "act_place")
    private String actPlace;

    @Column(name = "manager_name")
    private String managerName;

    @Column(name = "manager_tel")
    private String managerTel;

    @Column(name = "manager_fax")
    private String managerFax;

    @Column(name = "manager_email")
    private String managerEmail;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 7)
    private BigDecimal longitude;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** region 테이블(현구 담당) 참조. 도메인 간 결합을 피하기 위해 연관관계 대신 ID만 보관. */
    @Column(name = "region_id")
    private Long regionId;

    /** category 테이블(연석 담당, 아직 미생성) 참조. 같은 이유로 ID만 보관. */
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Builder
    private Posting(
            String extId,
            String title,
            PostingStatus status,
            String content,
            String recruitOrg,
            String registerOrg,
            LocalDate activityDate,
            LocalDate actStartDate,
            LocalDate actEndDate,
            String actStartTime,
            String actEndTime,
            LocalDate noticeStartDate,
            LocalDate noticeEndDate,
            String actWkdy,
            Integer recruitCount,
            Integer applicantCount,
            Boolean isAdult,
            Boolean isTeen,
            Boolean isGroup,
            Boolean isActive,
            String actPlace,
            String managerName,
            String managerTel,
            String managerFax,
            String managerEmail,
            BigDecimal latitude,
            BigDecimal longitude,
            Long regionId,
            Long categoryId) {
        this.extId = extId;
        this.title = title;
        this.status = status;
        this.content = content;
        this.recruitOrg = recruitOrg;
        this.registerOrg = registerOrg;
        this.activityDate = activityDate;
        this.actStartDate = actStartDate;
        this.actEndDate = actEndDate;
        this.actStartTime = actStartTime;
        this.actEndTime = actEndTime;
        this.noticeStartDate = noticeStartDate;
        this.noticeEndDate = noticeEndDate;
        this.actWkdy = actWkdy;
        this.recruitCount = recruitCount;
        this.applicantCount = applicantCount;
        this.isAdult = isAdult;
        this.isTeen = isTeen;
        this.isGroup = isGroup;
        this.isActive = isActive;
        this.actPlace = actPlace;
        this.managerName = managerName;
        this.managerTel = managerTel;
        this.managerFax = managerFax;
        this.managerEmail = managerEmail;
        this.latitude = latitude;
        this.longitude = longitude;
        this.regionId = regionId;
        this.categoryId = categoryId;
        this.createdAt = LocalDateTime.now();
    }
}
