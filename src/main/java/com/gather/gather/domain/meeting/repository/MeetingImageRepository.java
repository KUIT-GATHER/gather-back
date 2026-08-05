package com.gather.gather.domain.meeting.repository;

import com.gather.gather.domain.meeting.entity.MeetingImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingImageRepository extends JpaRepository<MeetingImage, Long> {

    List<MeetingImage> findByMeetingIdOrderBySortOrderAsc(Long meetingId);

    boolean existsByMeetingIdAndObjectKey(Long meetingId, String objectKey);

    @Query(
            """
            select image
            from MeetingImage image
            where image.meetingId in :meetingIds
              and image.sortOrder = (
                  select min(candidate.sortOrder)
                  from MeetingImage candidate
                  where candidate.meetingId = image.meetingId
              )
            """)
    List<MeetingImage> findRepresentativeImagesByMeetingIds(
            @Param("meetingIds") Collection<Long> meetingIds);
}
