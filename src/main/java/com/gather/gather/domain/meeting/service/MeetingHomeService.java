package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.meeting.dto.MeetingHomeResponse;
import com.gather.gather.domain.meeting.dto.MeetingMemberResponse;
import com.gather.gather.domain.meeting.dto.UpcomingActivityResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberRole;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 홈 화면 조회 전용 서비스(현승 담당).
 *
 * <p>모임 생성/가입 로직을 담은 {@code MeetingService}(연석)와 분리해, 홈 화면에 필요한 지역명·팀원 목록·연관 공고·가입 여부 조립만 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingHomeService {

    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final RegionRepository regionRepository;
    private final PostingRepository postingRepository;

    public MeetingHomeResponse getMeetingHome(Long meetingId) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();

        Meeting meeting =
                meetingRepository
                        .findByIdAndDeletedAtIsNull(meetingId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));

        List<MeetingMember> members =
                meetingMemberRepository.findAllByMeetingIdAndStatusFetchUser(
                        meetingId, MeetingMemberStatus.APPROVED);

        boolean member = members.stream().anyMatch(m -> m.getUser().getId().equals(userId));
        boolean host =
                members.stream()
                        .anyMatch(
                                m ->
                                        m.getUser().getId().equals(userId)
                                                && m.getRole() == MeetingMemberRole.HOST);

        MeetingMember pendingMembership =
                userId == null
                        ? null
                        : meetingMemberRepository
                                .findByMeeting_IdAndUser_IdAndStatus(
                                        meetingId, userId, MeetingMemberStatus.PENDING)
                                .orElse(null);

        List<MeetingMemberResponse> memberResponses =
                members.stream()
                        // 팀장(HOST)이 항상 첫 번째, 이후 순서는 가입 순
                        .sorted(
                                Comparator.comparing(
                                        (MeetingMember m) -> m.getRole() != MeetingMemberRole.HOST))
                        .map(MeetingMemberResponse::from)
                        .toList();

        String regionName =
                regionRepository
                        .findById(meeting.getRegionId())
                        .map(region -> region.getName())
                        .orElse(null);

        Posting linkedPosting = resolveLinkedPosting(meeting.getVolunteerPostingId());

        return new MeetingHomeResponse(
                meeting.getId(),
                meeting.getName(),
                meeting.getDescription(),
                meeting.getDeadline(),
                regionName,
                meeting.getCurrentMemberCount(),
                meeting.getMaxMember(),
                meeting.isTimeRecognized(),
                resolveDisplayStatus(meeting),
                meeting.getVolunteerPostingId() != null,
                linkedPosting == null ? null : linkedPosting.getId(),
                linkedPosting == null ? null : linkedPosting.getTitle(),
                meeting.getParticipationCondition(),
                memberResponses,
                linkedPosting == null ? null : UpcomingActivityResponse.from(linkedPosting),
                member,
                host,
                pendingMembership != null,
                pendingMembership == null ? null : pendingMembership.getId());
    }

    private Posting resolveLinkedPosting(Long volunteerPostingId) {
        if (volunteerPostingId == null) {
            return null;
        }
        return postingRepository.findById(volunteerPostingId).orElse(null);
    }

    /**
     * 표시용 모집 상태.
     *
     * <p>{@code MeetingService.resolveDisplayStatus}와 동일한 규칙을 홈 화면용으로 복제한 것. 향후 공용 유틸로 추출해 중복을 제거할
     * 것(팀 협의).
     */
    private MeetingStatus resolveDisplayStatus(Meeting meeting) {
        LocalDateTime now = LocalDateTime.now();

        if (meeting.getStatus() == MeetingStatus.COMPLETED || meeting.isActivityEnded(now)) {
            return MeetingStatus.COMPLETED;
        }
        if (meeting.getStatus() == MeetingStatus.CLOSED
                || meeting.isDeadlinePassed(now)
                || meeting.isFull()) {
            return MeetingStatus.CLOSED;
        }
        return MeetingStatus.RECRUITING;
    }
}
