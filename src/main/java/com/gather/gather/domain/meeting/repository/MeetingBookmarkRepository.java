package com.gather.gather.domain.meeting.repository;

import com.gather.gather.domain.meeting.entity.MeetingBookmark;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingBookmarkRepository extends JpaRepository<MeetingBookmark, Long> {

    boolean existsByUserIdAndMeetingId(Long userId, Long meetingId);

    Optional<MeetingBookmark> findByUserIdAndMeetingId(Long userId, Long meetingId);
}
