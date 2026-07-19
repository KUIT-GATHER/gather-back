package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.PostingSearchLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostingSearchLogRepository extends JpaRepository<PostingSearchLog, Long> {

    List<PostingSearchLog> findAllBySearchedAtAfter(LocalDateTime after);

    /**
     * derived delete 메서드(deleteBy...)는 {@code @Query} 없이 쓰면 엔티티를 조회해 개별 remove()하는 방식으로 동작해서, 로그가
     * 많아지면 정리 자체가 느려진다. 명시적 JPQL로 단일 bulk DELETE를 실행한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from PostingSearchLog l where l.searchedAt < :before")
    void deleteBySearchedAtBefore(@Param("before") LocalDateTime before);
}
