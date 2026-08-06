package com.gather.gather.domain.meeting.dto;

import com.gather.gather.domain.meeting.enums.MeetingStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 모임 홈 화면 응답.
 *
 * <p>가입/미가입 사용자가 같은 API를 쓰되, {@code member} 플래그로 하단 "모임 신청" 버튼 노출 여부를 결정한다. (미가입 → 신청 버튼 노출)
 *
 * <p>참고
 *
 * <ul>
 *   <li>{@code timeVerified}: 봉사시간 인증 기능이 아직 없어 항상 {@code false}. 프론트는 "확인 필요"로 렌더한다.
 *   <li>연관 공고가 없으면 {@code linkedPostingId}·{@code linkedPostingTitle}·{@code upcomingActivity}는 모두
 *       null.
 *   <li>모임 북마크(하트)는 별도 기능이라 이 응답에 포함하지 않는다.
 * </ul>
 */
public record MeetingHomeResponse(
        Long meetingId,
        String name,
        String description,
        LocalDateTime deadline,
        String regionName,
        Integer currentMemberCount,
        Integer maxMember,
        boolean timeVerified,
        MeetingStatus status,
        boolean basedOnPosting,
        Long linkedPostingId,
        String linkedPostingTitle,
        String participationCondition,
        List<MeetingMemberResponse> members,
        UpcomingActivityResponse upcomingActivity,
        boolean member,
        boolean host,
        /** 현재 사용자가 이 모임에 가입 신청을 하고 아직 승인/거절되지 않은 상태인지. 미로그인이거나 신청 이력이 없으면 false. */
        boolean pendingJoinRequested,
        /** 대기 중인 가입 신청(MeetingMember)의 id. pendingJoinRequested가 false면 null. */
        Long myPendingJoinRequestId) {}
