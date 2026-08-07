package com.gather.gather.domain.recruit.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.mypage.service.UserRecognizedMinutesService;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.enums.PostType;
import com.gather.gather.domain.post.repository.PostRepository;
import com.gather.gather.domain.recruit.dto.AttendanceUpdateRequest;
import com.gather.gather.domain.recruit.dto.ConfirmRecruitParticipantsResponse;
import com.gather.gather.domain.recruit.dto.RecruitManageItem;
import com.gather.gather.domain.recruit.dto.RecruitManageResponse;
import com.gather.gather.domain.recruit.dto.RecruitParticipantDetailResponse;
import com.gather.gather.domain.recruit.dto.RecruitParticipantListResponse;
import com.gather.gather.domain.recruit.dto.RecruitParticipantSummaryItem;
import com.gather.gather.domain.recruit.dto.RejectParticipantResponse;
import com.gather.gather.domain.recruit.dto.UpdateAttendanceResponse;
import com.gather.gather.domain.recruit.entity.MeetingRecruit;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipation;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import com.gather.gather.domain.recruit.entity.RecruitAttendanceStatus;
import com.gather.gather.domain.recruit.entity.RecruitConfirmationStatus;
import com.gather.gather.domain.recruit.repository.MeetingRecruitParticipationRepository;
import com.gather.gather.domain.recruit.repository.MeetingRecruitRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀장용 모집공고 관리(#12) 및 신청자 관리(#13) - 관리 목록, 신청자 목록/상세, 반려, 일괄 확정, 출석 처리.
 *
 * <p>모집공고 작성·조회·신청은 {@link MeetingRecruitService}가, 확정 이후 흐름(반려/확정/출석)은 이 서비스가 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingRecruitManagementService {

    private final MeetingRepository meetingRepository;
    private final PostRepository postRepository;
    private final MeetingRecruitRepository meetingRecruitRepository;
    private final MeetingRecruitParticipationRepository participationRepository;
    private final UserRepository userRepository;
    private final UserRecognizedMinutesService userRecognizedMinutesService;

    /** 모임에서 작성한 모집공고 전체(#12, 팀장 전용, 페이지네이션 없음). */
    public List<RecruitManageResponse> getManageList(Long meetingId) {
        Meeting meeting = getMeeting(meetingId);
        requireHost(meeting, SecurityUtil.getCurrentUserId());
        LocalDateTime now = LocalDateTime.now();
        return meetingRecruitRepository.findManageItemsByMeetingId(meetingId).stream()
                .map(item -> toManageResponse(item, now))
                .toList();
    }

    /** 신청자 목록(#13, 팀장 전용, 페이지네이션 없음, 개인정보 미포함). */
    public RecruitParticipantListResponse getParticipants(Long meetingId, Long postId) {
        Meeting meeting = getMeeting(meetingId);
        requireHost(meeting, SecurityUtil.getCurrentUserId());
        MeetingRecruit recruit = getRecruit(meetingId, postId);

        List<MeetingRecruitParticipation> participations =
                participationRepository.findAllByPostIdOrderByCreatedAtAsc(postId);
        Map<Long, String> nicknames = resolveNicknames(participations);

        List<RecruitParticipantSummaryItem> items =
                participations.stream()
                        .map(
                                p ->
                                        new RecruitParticipantSummaryItem(
                                                p.getId(),
                                                p.getUserId(),
                                                nicknames.get(p.getUserId()),
                                                p.getApplicantType(),
                                                p.getStatus(),
                                                p.getAttendanceStatus(),
                                                p.getCreatedAt()))
                        .toList();

        return new RecruitParticipantListResponse(
                postId,
                recruit.getConfirmationStatus(),
                recruit.getConfirmedAt(),
                recruit.getActivityStartAt(),
                recruit.getActivityEndAt(),
                items);
    }

    /** 신청자 상세(#13, 팀장 전용, 개인정보 포함). */
    public RecruitParticipantDetailResponse getParticipantDetail(
            Long meetingId, Long postId, Long participationId) {
        Meeting meeting = getMeeting(meetingId);
        requireHost(meeting, SecurityUtil.getCurrentUserId());
        getRecruit(meetingId, postId);
        MeetingRecruitParticipation participation = getParticipation(postId, participationId);
        User user = getUser(participation.getUserId());
        int totalRecognizedMinutes =
                userRecognizedMinutesService.getTotalRecognizedMinutes(participation.getUserId());
        return new RecruitParticipantDetailResponse(
                participation.getId(),
                user.getId(),
                user.getNickname(),
                participation.getApplicantType(),
                participation.getStatus(),
                participation.getAttendanceStatus(),
                user.getPhoneNumber(),
                user.getBirthDate(),
                user.getActivityRegion() != null ? user.getActivityRegion().getId() : null,
                user.getActivityRegion() != null ? user.getActivityRegion().getName() : null,
                user.getInterestCategories(),
                totalRecognizedMinutes,
                participation.getCreatedAt());
    }

    /** 신청자 반려: APPLIED -> REJECTED. */
    @Transactional
    public RejectParticipantResponse rejectParticipant(Long meetingId, Long postId, Long participationId) {
        Meeting meeting = getMeeting(meetingId);
        requireHost(meeting, SecurityUtil.getCurrentUserId());
        getRecruit(meetingId, postId);
        MeetingRecruitParticipation participation = getParticipation(postId, participationId);
        if (participation.getStatus() != MeetingRecruitParticipationStatus.APPLIED) {
            throw new BusinessException(ErrorCode.RECRUIT_PARTICIPANT_NOT_APPLIED);
        }
        participation.reject();
        return new RejectParticipantResponse(
                participation.getId(),
                participation.getStatus(),
                participation.getAttendanceStatus(),
                participation.getUpdatedAt());
    }

    /** 현재 APPLIED 신청자 전체를 CONFIRMED로 일괄 확정한다. 신청자가 0명이거나 이미 확정됐으면 거부한다(재확정 불가). */
    @Transactional
    public ConfirmRecruitParticipantsResponse confirmParticipants(Long meetingId, Long postId) {
        Meeting meeting = getMeeting(meetingId);
        requireHost(meeting, SecurityUtil.getCurrentUserId());
        MeetingRecruit recruit = getRecruit(meetingId, postId);
        if (recruit.getConfirmationStatus() == RecruitConfirmationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.RECRUIT_ALREADY_CONFIRMED);
        }
        List<MeetingRecruitParticipation> applied =
                participationRepository.findAllByPostIdAndStatus(
                        postId, MeetingRecruitParticipationStatus.APPLIED);
        if (applied.isEmpty()) {
            throw new BusinessException(ErrorCode.RECRUIT_NO_APPLICANTS_TO_CONFIRM);
        }
        LocalDateTime now = LocalDateTime.now();
        applied.forEach(MeetingRecruitParticipation::confirm);
        recruit.confirm(now);
        return new ConfirmRecruitParticipantsResponse(
                postId, recruit.getConfirmationStatus(), recruit.getConfirmedAt(), applied.size());
    }

    /**
     * 출석 처리(PRESENT/ABSENT). 참가 인원 확정 후, 활동 종료 시각이 지난 뒤에만 가능하다. PRESENT면 완료(COMPLETED)로 전환하고
     * timeRecognized=true면 인정 시간을 반영하며, ABSENT면 확정(CONFIRMED)을 유지하고 반영된 인정 시간을 차감한다. 동일 상태 재요청은
     * 멱등하게 무시한다(엔티티 markPresent/markAbsent에서 처리).
     */
    @Transactional
    public UpdateAttendanceResponse updateAttendance(
            Long meetingId, Long postId, Long participationId, AttendanceUpdateRequest request) {
        Meeting meeting = getMeeting(meetingId);
        requireHost(meeting, SecurityUtil.getCurrentUserId());
        MeetingRecruit recruit = getRecruit(meetingId, postId);

        if (request.attendanceStatus() == RecruitAttendanceStatus.UNSET) {
            throw new BusinessException(ErrorCode.RECRUIT_ATTENDANCE_INVALID_STATUS);
        }
        LocalDateTime now = LocalDateTime.now();
        if (recruit.getConfirmationStatus() != RecruitConfirmationStatus.CONFIRMED
                || !recruit.isActivityEnded(now)) {
            throw new BusinessException(ErrorCode.RECRUIT_ATTENDANCE_NOT_ALLOWED);
        }

        MeetingRecruitParticipation participation = getParticipation(postId, participationId);
        boolean attendanceEligible =
                participation.getStatus() == MeetingRecruitParticipationStatus.CONFIRMED
                        || participation.getStatus() == MeetingRecruitParticipationStatus.COMPLETED;
        if (!attendanceEligible) {
            throw new BusinessException(ErrorCode.RECRUIT_ATTENDANCE_NOT_ALLOWED);
        }

        if (request.attendanceStatus() == RecruitAttendanceStatus.PRESENT) {
            int minutes =
                    recruit.isTimeRecognized() && recruit.getRecognizedMinutes() != null
                            ? recruit.getRecognizedMinutes()
                            : 0;
            participation.markPresent(minutes);
        } else {
            participation.markAbsent();
        }

        return new UpdateAttendanceResponse(
                participation.getId(),
                participation.getStatus(),
                participation.getAttendanceStatus(),
                participation.getRecognizedMinutesApplied(),
                participation.getUpdatedAt());
    }

    /**
     * 신청 마감 시각이 지났는데 아직 확정되지 않은 모집공고를 자동 확정한다(스케줄러 전용). 남아 있는 APPLIED 신청자는 CONFIRMED로
     * 전환하고, 신청자가 0명이어도 모집공고 자체는 확정 처리한다(별도 신청자 상태 변경은 없음).
     */
    @Transactional
    public int autoConfirmExpiredRecruits() {
        LocalDateTime now = LocalDateTime.now();
        List<MeetingRecruit> expired =
                meetingRecruitRepository.findAllByConfirmationStatusAndApplyDeadlineAtBefore(
                        RecruitConfirmationStatus.UNCONFIRMED, now);
        for (MeetingRecruit recruit : expired) {
            participationRepository
                    .findAllByPostIdAndStatus(recruit.getPostId(), MeetingRecruitParticipationStatus.APPLIED)
                    .forEach(MeetingRecruitParticipation::confirm);
            recruit.confirm(now);
        }
        return expired.size();
    }

    private RecruitManageResponse toManageResponse(RecruitManageItem item, LocalDateTime now) {
        boolean open =
                item.confirmationStatus() == RecruitConfirmationStatus.UNCONFIRMED
                        && !now.isAfter(item.applyDeadlineAt());
        return new RecruitManageResponse(
                item.postId(),
                item.title(),
                item.place(),
                item.activityStartAt(),
                item.activityEndAt(),
                item.applyDeadlineAt(),
                item.appliedCount(),
                item.maxParticipants(),
                item.external(),
                open,
                item.confirmationStatus(),
                item.confirmedAt(),
                true);
    }

    private Map<Long, String> resolveNicknames(List<MeetingRecruitParticipation> participations) {
        Set<Long> userIds =
                participations.stream().map(MeetingRecruitParticipation::getUserId).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
    }

    private MeetingRecruitParticipation getParticipation(Long postId, Long participationId) {
        MeetingRecruitParticipation participation =
                participationRepository
                        .findById(participationId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RECRUIT_PARTICIPANT_NOT_FOUND));
        if (!participation.getPostId().equals(postId)) {
            throw new BusinessException(ErrorCode.RECRUIT_PARTICIPANT_NOT_FOUND);
        }
        return participation;
    }

    private MeetingRecruit getRecruit(Long meetingId, Long postId) {
        Post post =
                postRepository
                        .findByIdFetchUser(postId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RECRUIT_NOT_FOUND));
        if (!post.getMeeting().getId().equals(meetingId) || post.getType() != PostType.RECRUIT) {
            throw new BusinessException(ErrorCode.RECRUIT_NOT_FOUND);
        }
        return meetingRecruitRepository
                .findByPostId(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECRUIT_NOT_FOUND));
    }

    private User getUser(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
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
