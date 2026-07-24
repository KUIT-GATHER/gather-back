package com.gather.gather.domain.meeting.repository;

import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingMemberRepository extends JpaRepository<MeetingMember, Long> {
    boolean existsByMeeting_IdAndUser_IdAndStatus(
            Long meetingId, Long userId, MeetingMemberStatus status);

    /** 특정 사용자의 특정 모임 멤버십 단건 조회(권한 분기·가입 여부 판정용). */
    Optional<MeetingMember> findByMeeting_IdAndUser_IdAndStatus(
            Long meetingId, Long userId, MeetingMemberStatus status);

    /** 모임 홈 팀원 목록. 닉네임 접근 시 N+1을 막기 위해 user를 함께 로딩한다. */
    @Query(
            """
            SELECT mm
            FROM MeetingMember mm
            JOIN FETCH mm.user
            WHERE mm.meeting.id = :meetingId
              AND mm.status = :status
            ORDER BY mm.joinedAt ASC
            """)
    List<MeetingMember> findAllByMeetingIdAndStatusFetchUser(
            @Param("meetingId") Long meetingId, @Param("status") MeetingMemberStatus status);

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

    @Query(
            """
            SELECT mm
            FROM MeetingMember mm
            JOIN FETCH mm.meeting m
            WHERE mm.user.id = :userId
              AND mm.status = :status
              AND m.id IN :meetingIds
              AND m.deletedAt IS NULL
            """)
    List<MeetingMember> findAllByUserIdAndStatusAndMeetingIdInFetchMeeting(
            @Param("userId") Long userId,
            @Param("status") MeetingMemberStatus status,
            @Param("meetingIds") List<Long> meetingIds);
}
