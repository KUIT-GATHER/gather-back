package com.gather.gather.domain.meeting.repository;

import com.gather.gather.domain.meeting.entity.MeetingSearchLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingSearchLogRepository extends JpaRepository<MeetingSearchLog, Long> {

    @Query("select l.keyword from MeetingSearchLog l where l.searchedAt > :after")
    List<String> findKeywordsBySearchedAtAfter(@Param("after") LocalDateTime after);

    @Modifying(clearAutomatically = true)
    @Query("delete from MeetingSearchLog l where l.searchedAt < :before")
    void deleteBySearchedAtBefore(@Param("before") LocalDateTime before);
}
