package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.PostingSearchLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostingSearchLogRepository extends JpaRepository<PostingSearchLog, Long> {

    /**
     * 집계 배치는 keyword 문자열만 필요하다. Entity 전체를 조회하면 영속성 컨텍스트가 각 로우를 엔티티로 관리하는 오버헤드가 붙으므로, keyword 컬럼만
     * 프로젝션해서 가져온다.
     */
    @Query("select l.keyword from PostingSearchLog l where l.searchedAt > :after")
    List<String> findKeywordsBySearchedAtAfter(@Param("after") LocalDateTime after);

    /**
     * derived delete 메서드(deleteBy...)는 {@code @Query} 없이 쓰면 엔티티를 조회해 개별 remove()하는 방식으로 동작해서, 로그가
     * 많아지면 정리 자체가 느려진다. 명시적 JPQL로 단일 bulk DELETE를 실행한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from PostingSearchLog l where l.searchedAt < :before")
    void deleteBySearchedAtBefore(@Param("before") LocalDateTime before);
}
