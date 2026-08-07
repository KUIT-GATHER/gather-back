package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.meeting.dto.MeetingJoinRequestDetailResponse;
import com.gather.gather.domain.meeting.dto.MeetingJoinRequestResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.mypage.service.UserRecognizedMinutesService;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가입 신청 상태별 조회·상세·거절 복구(#10). 목록/승인/거절 자체는 기존 {@link MeetingService}를 그대로 사용한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingJoinRequestManagementService {

    private static final List<MeetingMemberStatus> DEFAULT_STATUSES =
            List.of(MeetingMemberStatus.PENDING, MeetingMemberStatus.APPROVED, MeetingMemberStatus.REJECTED);

    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final UserRecognizedMinutesService userRecognizedMinutesService;

    public List<MeetingJoinRequestResponse> getJoinRequests(Long meetingId, MeetingMemberStatus status) {
        Meeting meeting = getMeeting(meetingId);
        requireHost(meeting, SecurityUtil.getCurrentUserId());
        List<MeetingMemberStatus> statuses = status != null ? List.of(status) : DEFAULT_STATUSES;
        return meetingMemberRepository.findAllByMeetingIdAndStatusInFetchUser(meetingId, statuses).stream()
                .map(MeetingJoinRequestResponse::from)
                .toList();
    }

    public MeetingJoinRequestDetailResponse getJoinRequestDetail(Long meetingId, Long joinRequestId) {
        Meeting meeting = getMeeting(meetingId);
        requireHost(meeting, SecurityUtil.getCurrentUserId());
        MeetingMember member =
                meetingMemberRepository
                        .findByIdAndMeetingIdFetchUser(joinRequestId, meetingId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.MEETING_JOIN_REQUEST_NOT_FOUND));
        return toDetail(member);
    }

    @Transactional
    public MeetingJoinRequestResponse restoreToPending(Long meetingId, Long joinRequestId) {
        Meeting meeting = getMeeting(meetingId);
        requireHost(meeting, SecurityUtil.getCurrentUserId());
        MeetingMember member =
                meetingMemberRepository
                        .findRejectedByIdAndMeetingIdForUpdate(joinRequestId, meetingId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.MEETING_JOIN_REQUEST_NOT_FOUND));
        member.requestAgain();
        return MeetingJoinRequestResponse.from(member);
    }

    private MeetingJoinRequestDetailResponse toDetail(MeetingMember member) {
        User user = member.getUser();
        Region region = user.getActivityRegion();
        int totalRecognizedMinutes = userRecognizedMinutesService.getTotalRecognizedMinutes(user.getId());
        return new MeetingJoinRequestDetailResponse(
                member.getId(),
                user.getId(),
                user.getNickname(),
                member.getStatus(),
                member.getJoinedAt(),
                user.getPhoneNumber(),
                user.getBirthDate(),
                region != null ? region.getId() : null,
                region != null ? region.getName() : null,
                user.getInterestCategories(),
                totalRecognizedMinutes);
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
