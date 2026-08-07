package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.meeting.dto.MeetingMemberDetailResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.mypage.service.UserRecognizedMinutesService;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipation;
import com.gather.gather.domain.recruit.repository.MeetingRecruitParticipationRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임원 상세 조회·내보내기(#11). 게시글·댓글·완료된 활동 기록은 내보내기와 무관하게 그대로 유지한다(삭제하지 않음).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingMemberManagementService {

    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final MeetingRecruitParticipationRepository recruitParticipationRepository;
    private final UserRecognizedMinutesService userRecognizedMinutesService;

    public MeetingMemberDetailResponse getMemberDetail(Long meetingId, Long targetUserId) {
        Meeting meeting = getMeeting(meetingId);
        requireHost(meeting, SecurityUtil.getCurrentUserId());
        MeetingMember member =
                meetingMemberRepository
                        .findByMeeting_IdAndUser_IdAndStatus(
                                meetingId, targetUserId, MeetingMemberStatus.APPROVED)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_MEMBER_NOT_FOUND));
        User user = member.getUser();
        Region region = user.getActivityRegion();
        int totalRecognizedMinutes = userRecognizedMinutesService.getTotalRecognizedMinutes(targetUserId);
        return new MeetingMemberDetailResponse(
                user.getId(),
                user.getNickname(),
                member.getRole(),
                user.getPhoneNumber(),
                user.getBirthDate(),
                region != null ? region.getId() : null,
                region != null ? region.getName() : null,
                user.getInterestCategories(),
                totalRecognizedMinutes);
    }

    /**
     * 팀장이 멤버를 내보낸다. 아직 종료되지 않은 활동에 CONFIRMED 상태로 참가 중이면 409로 거부하고, APPLIED 상태의 신청만 있다면
     * CANCELLED로 정리한 뒤 내보낸다.
     */
    @Transactional
    public void removeMember(Long meetingId, Long targetUserId) {
        Meeting meeting = getMeeting(meetingId);
        Long hostUserId = SecurityUtil.getCurrentUserId();
        requireHost(meeting, hostUserId);
        if (meeting.getHost().getId().equals(targetUserId)) {
            throw new BusinessException(ErrorCode.MEETING_MEMBER_HOST_CANNOT_BE_REMOVED);
        }

        MeetingMember target =
                meetingMemberRepository
                        .findApprovedByMeetingIdAndUserIdForUpdate(meetingId, targetUserId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_MEMBER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        if (recruitParticipationRepository.existsActiveConfirmedActivity(meetingId, targetUserId, now)) {
            throw new BusinessException(ErrorCode.MEETING_MEMBER_HAS_ACTIVE_ACTIVITY);
        }
        recruitParticipationRepository
                .findApplied(meetingId, targetUserId)
                .forEach(MeetingRecruitParticipation::cancel);

        target.remove();
        meeting.decreaseMemberCount();
    }

    private Meeting getMeeting(Long meetingId) {
        return meetingRepository
                .findByIdAndDeletedAtIsNull(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
    }

    private void requireHost(Meeting meeting, Long userId) {
        if (!meeting.getHost().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.MEETING_HOST_ONLY);
        }
    }
}
