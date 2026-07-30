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

    /** 활동 종료일(actEndDate)이 지난 공고를 일괄 비활성화한다. */
    @Modifying(clearAutomatically = true)
    @Query(
            """
            update Posting p set p.isActive = false, p.updatedAt = :now
            where p.isActive = true
              and p.actEndDate is not null
              and p.actEndDate < :today
            """)
    int deactivateExpired(@Param("today") LocalDate today, @Param("now") LocalDateTime now);
}
