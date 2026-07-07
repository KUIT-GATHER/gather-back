package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.dto.MeetingCreateRequest;
import com.gather.gather.domain.meeting.dto.MeetingDetailResponse;
import com.gather.gather.domain.meeting.dto.MeetingResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public MeetingResponse createMeeting(MeetingCreateRequest request) {
        validateMeetingTime(request.deadline(), request.activityStartAt(), request.activityEndAt());

        Long userId = SecurityUtil.getCurrentUserId();
        User host = getUser(userId);

        Meeting meeting =
                Meeting.create(
                        request.name(),
                        request.description(),
                        request.maxMember(),
                        request.deadline(),
                        request.memo(),
                        request.categoryId(),
                        request.regionId(),
                        host,
                        request.participationCondition(),
                        request.volunteerPostingId(),
                        request.activityStartAt(),
                        request.activityEndAt());

        Meeting savedMeeting = meetingRepository.save(meeting);

        MeetingMember hostMember = MeetingMember.createHost(host, savedMeeting);
        meetingMemberRepository.save(hostMember);

        return MeetingResponse.from(savedMeeting, resolveDisplayStatus(savedMeeting));
    }

    public List<MeetingResponse> getMeetings(
            String keyword, Long regionId, Long categoryId, MeetingStatus status) {
        return meetingRepository.searchMeetings(keyword, regionId, categoryId, status).stream()
                .map(meeting -> MeetingResponse.from(meeting, resolveDisplayStatus(meeting)))
                .toList();
    }

    public MeetingDetailResponse getMeeting(Long meetingId) {
        Meeting meeting = getMeetingEntity(meetingId);
        return MeetingDetailResponse.from(meeting, resolveDisplayStatus(meeting));
    }

    private User getUser(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Meeting getMeetingEntity(Long meetingId) {
        return meetingRepository
                .findByIdAndDeletedAtIsNull(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
    }

    private void validateMeetingTime(
            LocalDateTime deadline, LocalDateTime activityStartAt, LocalDateTime activityEndAt) {
        if (!activityStartAt.isBefore(activityEndAt)) {
            throw new BusinessException(ErrorCode.INVALID_MEETING_TIME);
        }

        if (deadline.isAfter(activityStartAt)) {
            throw new BusinessException(ErrorCode.INVALID_MEETING_TIME);
        }
    }

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
