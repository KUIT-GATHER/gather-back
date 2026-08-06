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
 *   <li>{@code timeRecognized}: 봉사시간 인정 여부(공고 기반 모임 전용). 자유 모임은 항상 {@code false}.
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
        boolean timeRecognized,
        MeetingStatus status,
        boolean basedOnPosting,
        Long linkedPostingId,
        String linkedPostingTitle,
        String participationCondition,
        List<MeetingMemberResponse> members,
        UpcomingActivityResponse upcomingActivity,
        boolean member,
        boolean host) {}
