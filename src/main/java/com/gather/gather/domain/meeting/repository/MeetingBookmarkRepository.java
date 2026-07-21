package com.gather.gather.domain.meeting.repository;

import com.gather.gather.domain.meeting.entity.MeetingBookmark;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingBookmarkRepository extends JpaRepository<MeetingBookmark, Long> {

    boolean existsByUserIdAndMeetingId(Long userId, Long meetingId);

    Optional<MeetingBookmark> findByUserIdAndMeetingId(Long userId, Long meetingId);

    @Modifying
    @Query(
            "DELETE FROM MeetingBookmark b WHERE b.userId = :userId AND b.meetingId ="
                    + " :meetingId")
    int deleteByUserIdAndMeetingId(
            @Param("userId") Long userId, @Param("meetingId") Long meetingId);
}
