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

    /**
     * DB 생성 컬럼(V67, {@code COALESCE(notice_end_date, '9999-12-31')}). notice_end_date가 없는 상시모집 공고를
     * 정렬·인덱스 상 항상 맨 뒤로 보내기 위한 값으로, {@link
     * com.gather.gather.domain.posting.repository.PostingRepositoryImpl}의 Criteria API 경로({@code
     * root.get("noticeEndDateSortKey")})로만 참조되는 WHERE/ORDER BY 전용 컬럼이다. 애플리케이션 코드에서 읽어 쓸 일이 없으므로
     * getter를 노출하지 않는다 — 같은 영속성 컨텍스트 내에서 저장 직후 조회하면(Hibernate가 생성 컬럼 값을 재조회하지 않아) stale 값을 반환할 수 있는
     * 트랩이기도 하다.
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "notice_end_date_sort_key", insertable = false, updatable = false)
    private LocalDate noticeEndDateSortKey;

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

    /** 해당 공고의 봉사 실행 날짜가 지난 공고는 false로 */
    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "act_place")
    private String actPlace;

    @Column(name = "post_address")
    private String postAddress;

    @Column(name = "manager_name")
    private String managerName;

    @Column(name = "manager_tel")
    private String managerTel;

    @Column(name = "manager_fax")
    private String managerFax;

    @Column(name = "manager_email")
    private String managerEmail;

    @Column(name = "manager_address")
    private String managerAddress;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private PostingCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private PostingSource source;

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
            String postAddress,
            String managerName,
            String managerTel,
            String managerFax,
            String managerEmail,
            String managerAddress,
            BigDecimal latitude,
            BigDecimal longitude,
            Long regionId,
            PostingCategory category,
            PostingSource source) {
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
        this.postAddress = postAddress;
        this.managerName = managerName;
        this.managerTel = managerTel;
        this.managerFax = managerFax;
        this.managerEmail = managerEmail;
        this.managerAddress = managerAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.regionId = regionId;
        this.category = category;
        this.source = source;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /** 활동종료일이 지났는지 여부. actEndDate가 없으면(개별활동일만 있는 공고) activityDate를 종료일로 취급한다. */
    public boolean isActivityEnded(LocalDate today) {
        LocalDate endDate = getEffectiveActivityDate();
        return endDate != null && !endDate.isAfter(today);
    }

    /** 뱃지 판정(활동일 기준 연속 참여 월 계산) 등에 쓰이는 실질 활동일 — actEndDate가 없으면 activityDate로 대체한다. */
    public LocalDate getEffectiveActivityDate() {
        return actEndDate != null ? actEndDate : activityDate;
    }

    /** 목록조회로 재확인된 기존 공고의 갱신 가능 필드만 반영한다(동기화 배치 update 경로). */
    public void updateFromSync(
            String title,
            PostingStatus status,
            String recruitOrg,
            LocalDate actStartDate,
            LocalDate actEndDate,
            LocalDate noticeStartDate,
            LocalDate noticeEndDate,
            String actPlace,
            Boolean isAdult,
            Boolean isTeen,
            Long regionId,
            PostingCategory category) {
        this.title = title;
        this.status = status;
        this.recruitOrg = recruitOrg;
        this.actStartDate = actStartDate;
        this.actEndDate = actEndDate;
        this.noticeStartDate = noticeStartDate;
        this.noticeEndDate = noticeEndDate;
        this.actPlace = actPlace;
        this.isAdult = isAdult;
        this.isTeen = isTeen;
        this.regionId = regionId;
        this.category = category;
        this.isActive = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * VMS 목록카드 재확인 시 갱신 가능한 필드만 반영한다. VMS 목록카드는 1365 목록조회와 달리 category/actPlace/regionId를 주지
     * 않아(상세페이지에만 있음), 이 필드들은 최초 등록(신규 insert) 시점 값을 그대로 유지하고 재조회하지 않는다 — 매번 상세페이지까지 다시 긁는 것보다 요청량을
     * 줄이는 쪽을 택했다(구조적 사실은 잘 안 바뀐다는 전제).
     */
    public void updateFromVmsSync(
            String title,
            PostingStatus status,
            String recruitOrg,
            LocalDate actStartDate,
            LocalDate actEndDate,
            boolean isActive) {
        this.title = title;
        this.status = status;
        this.recruitOrg = recruitOrg;
        this.actStartDate = actStartDate;
        this.actEndDate = actEndDate;
        this.isActive = isActive;
        this.updatedAt = LocalDateTime.now();
    }
}
