package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.PostingLocation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostingLocationRepository extends JpaRepository<PostingLocation, Long> {

    List<PostingLocation> findAllByPostingIdOrderByLocationSeq(Long postingId);

    /** 지도 조회(#186) 등 여러 공고의 2·3번째 장소를 배치로 조회할 때 사용한다(N+1 방지). */
    List<PostingLocation> findAllByPostingIdInOrderByPostingIdAscLocationSeqAsc(
            Collection<Long> postingIds);
}
