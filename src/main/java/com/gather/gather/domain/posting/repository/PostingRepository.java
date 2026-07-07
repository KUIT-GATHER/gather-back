package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostingRepository extends JpaRepository<Posting, Long> {

    Optional<Posting> findByExtId(String extId);

    @Query(
            """
            select p from Posting p
            where p.status = :status
              and (:regionIds is null or p.regionId in :regionIds)
              and (:noticeStartFrom is null or p.noticeStartDate >= :noticeStartFrom)
              and (:noticeEndTo is null or p.noticeEndDate <= :noticeEndTo)
            """)
    Page<Posting> search(
            @Param("status") PostingStatus status,
            @Param("regionIds") List<Long> regionIds,
            @Param("noticeStartFrom") LocalDate noticeStartFrom,
            @Param("noticeEndTo") LocalDate noticeEndTo,
            Pageable pageable);
}
