package com.gather.gather.domain.meeting.repository;

import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingMemberRepository extends JpaRepository<MeetingMember, Long> {
    Optional<MeetingMember> findByMeeting_IdAndUser_Id(Long meetingId, Long userId);

    /** 뱃지 판정용 — 완료된 모임에서 승인된 멤버십만 조회한다(완료 횟수, 연속 참여 월 계산). */
    @Query(
            """
            SELECT mm
            FROM MeetingMember mm
            JOIN FETCH mm.meeting m
            WHERE mm.user.id = :userId
              AND mm.status = :status
              AND m.status = :meetingStatus
              AND m.deletedAt IS NULL
            """)
    List<MeetingMember> findAllByUserIdAndStatusAndMeetingStatus(
            @Param("userId") Long userId,
            @Param("status") MeetingMemberStatus status,
            @Param("meetingStatus")
                    com.gather.gather.domain.meeting.enums.MeetingStatus meetingStatus);

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
            JOIN FETCH mm.user
            WHERE mm.meeting.id = :meetingId
              AND mm.status = com.gather.gather.domain.meeting.enums.MeetingMemberStatus.PENDING
            ORDER BY mm.createdAt ASC
            """)
    List<MeetingMember> findPendingByMeetingIdFetchUser(@Param("meetingId") Long meetingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT mm
            FROM MeetingMember mm
            JOIN FETCH mm.user
            JOIN FETCH mm.meeting
            WHERE mm.id = :joinRequestId
              AND mm.meeting.id = :meetingId
              AND mm.status = com.gather.gather.domain.meeting.enums.MeetingMemberStatus.PENDING
            """)
    Optional<MeetingMember> findPendingByIdAndMeetingIdForUpdate(
            @Param("joinRequestId") Long joinRequestId, @Param("meetingId") Long meetingId);

    /** 신청자 본인이 대기 중인 가입 신청을 취소할 때 사용(승인 처리와의 경합 방지를 위해 락을 건다). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT mm
            FROM MeetingMember mm
            WHERE mm.meeting.id = :meetingId
              AND mm.user.id = :userId
              AND mm.status = com.gather.gather.domain.meeting.enums.MeetingMemberStatus.PENDING
            """)
    Optional<MeetingMember> findPendingByMeetingIdAndUserIdForUpdate(
            @Param("meetingId") Long meetingId, @Param("userId") Long userId);

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

    /**
     * 마이페이지 활동 캘린더용 — 승인된 멤버로 참여 중인 모임 중 활동기간(activityStartAt~activityEndAt, 종료일이 없으면 시작일과 동일한 것으로
     * 간주)이 조회 월과 겹치는 모임만 조회한다. 활동기간이 아예 없는 자유 모임은 캘린더에 표시할 날짜가 없어 제외한다.
     */
    @Query(
            """
            SELECT mm
            FROM MeetingMember mm
            JOIN FETCH mm.meeting m
            WHERE mm.user.id = :userId
              AND mm.status = com.gather.gather.domain.meeting.enums.MeetingMemberStatus.APPROVED
              AND m.deletedAt IS NULL
              AND m.activityStartAt IS NOT NULL
              AND m.activityStartAt < :monthEndExclusive
              AND COALESCE(m.activityEndAt, m.activityStartAt) >= :monthStartInclusive
            """)
    List<MeetingMember> findApprovedForCalendar(
            @Param("userId") Long userId,
            @Param("monthStartInclusive") LocalDateTime monthStartInclusive,
            @Param("monthEndExclusive") LocalDateTime monthEndExclusive);
}
