package com.gather.gather.domain.meeting.repository;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MeetingMemberRepository extends JpaRepository<MeetingMember, Long> {
    boolean existsByMeeting_IdAndUser_IdAndStatus(
            Long meetingId,
            Long userId,
            MeetingMemberStatus status);
    List<MeetingMember> findAllByUser_IdAndStatus(
            Long userId,
            MeetingMemberStatus status);
}