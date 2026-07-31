package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostingRepository extends JpaRepository<Posting, Long> {

    Optional<Posting> findByExtId(String extId);

    /**
     * 활동 종료일이 지난 공고를 일괄 비활성화한다. actEndDate가 있으면 그 값을, 없으면(개별활동일만 있는 공고) activityDate를 종료일로 취급한다.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            """
            update Posting p set p.isActive = false, p.updatedAt = :now
            where p.isActive = true
              and (
                (p.actEndDate is not null and p.actEndDate < :today)
                or (p.actEndDate is null and p.activityDate < :today)
              )
            """)
    int deactivateExpired(@Param("today") LocalDate today, @Param("now") LocalDateTime now);

    /**
     * 비활성화된 지 오래된 공고의 content(최대 용량 컬럼)를 비운다. isActive=false이고 effective 종료일(actEndDate, 없으면
     * activityDate)이 cutoffDate 이전인 공고가 대상이다.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            """
            update Posting p set p.content = null, p.updatedAt = :now
            where p.isActive = false
              and p.content is not null
              and coalesce(p.actEndDate, p.activityDate) < :cutoffDate
            """)
    int clearExpiredContent(
            @Param("cutoffDate") LocalDate cutoffDate, @Param("now") LocalDateTime now);

    @Query(
            """
            select p from Posting p
            where p.status = :status
              and (:regionIds is null or p.regionId in :regionIds)
              and (:noticeStartFrom is null or p.noticeStartDate >= :noticeStartFrom)
              and (:noticeEndTo is null or p.noticeEndDate <= :noticeEndTo)
              and (:keyword is null
                   or p.title like concat('%', :keyword, '%')
                   or p.recruitOrg like concat('%', :keyword, '%'))
              and (:category is null or p.category = :category)
            """)
    Page<Posting> search(
            @Param("status") PostingStatus status,
            @Param("regionIds") List<Long> regionIds,
            @Param("noticeStartFrom") LocalDate noticeStartFrom,
            @Param("noticeEndTo") LocalDate noticeEndTo,
            @Param("keyword") String keyword,
            @Param("category") PostingCategory category,
            Pageable pageable);
}
