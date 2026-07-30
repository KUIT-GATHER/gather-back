package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostingRepositoryCustom {

    /**
     * status가 null이면 RECRUITING과 CLOSED를 모두 조회하되, RECRUITING을 우선 정렬하고 그 안에서 pageable의 정렬을 적용한다.
     * status가 주어지면 해당 상태만 조회한다(우선순위 정렬은 의미가 없어 생략).
     */
    Page<Posting> search(
            PostingStatus status,
            List<Long> regionIds,
            LocalDate noticeStartFrom,
            LocalDate noticeEndTo,
            String keyword,
            PostingCategory category,
            Pageable pageable);
}
