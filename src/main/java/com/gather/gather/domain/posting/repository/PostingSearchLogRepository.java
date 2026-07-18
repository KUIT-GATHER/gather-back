package com.gather.gather.domain.posting.repository;

import com.gather.gather.domain.posting.entity.PostingSearchLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface PostingSearchLogRepository extends JpaRepository<PostingSearchLog, Long> {

    List<PostingSearchLog> findAllBySearchedAtAfter(LocalDateTime after);

    @Modifying(clearAutomatically = true)
    void deleteBySearchedAtBefore(LocalDateTime before);
}
