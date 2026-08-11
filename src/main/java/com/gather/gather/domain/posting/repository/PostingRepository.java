package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.Posting;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostingRepository extends JpaRepository<Posting, Long>, PostingRepositoryCustom {

    Optional<Posting> findByExtId(String extId);

    /** 소스(1365/VMS)와 무관하게 title+activityDate가 완전히 같은 공고가 이미 있는지 확인한다(교차 소스 중복 저장 방지). */
    boolean existsByTitleAndActivityDate(String title, LocalDate activityDate);

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
     * activityDate)이 cutoffDate 이하(= 1개월 이상 경과)인 공고가 대상이다.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            """
            update Posting p set p.content = null, p.updatedAt = :now
            where p.isActive = false
              and p.content is not null
              and coalesce(p.actEndDate, p.activityDate) <= :cutoffDate
            """)
    int clearExpiredContent(
            @Param("cutoffDate") LocalDate cutoffDate, @Param("now") LocalDateTime now);
}
