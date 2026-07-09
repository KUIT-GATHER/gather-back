package com.gather.gather.domain.meeting.repository;

import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingMemberRepository extends JpaRepository<MeetingMember, Long> {
    boolean existsByMeeting_IdAndUser_IdAndStatus(
            Long meetingId, Long userId, MeetingMemberStatus status);

    @Query(
            """
            SELECT mm
            FROM MeetingMember mm
            JOIN FETCH mm.meeting m
            WHERE mm.user.id = :userId
              AND mm.status = :status
              AND m.deletedAt IS NULL
            ORDER BY m.createdAt DESC
            """)
    List<MeetingMember> findAllByUserIdAndStatusFetchMeeting(
            @Param("userId") Long userId, @Param("status") MeetingMemberStatus status);
}
