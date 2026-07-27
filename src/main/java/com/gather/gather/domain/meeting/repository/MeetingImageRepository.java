package com.gather.gather.domain.meeting.repository;

import com.gather.gather.domain.meeting.entity.MeetingImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingImageRepository extends JpaRepository<MeetingImage, Long> {

    List<MeetingImage> findByMeetingIdOrderBySortOrderAsc(Long meetingId);

    boolean existsByMeetingIdAndObjectKey(Long meetingId, String objectKey);
}
