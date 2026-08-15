package com.gather.gather.domain.recruit.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberRole;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.event.MeetingPostNotificationRequestedEvent;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.enums.PostType;
import com.gather.gather.domain.post.repository.PostRepository;
import com.gather.gather.domain.recruit.dto.RecruitCreateRequest;
import com.gather.gather.domain.recruit.dto.RecruitDetailResponse;
import com.gather.gather.domain.recruit.dto.RecruitParticipationAction;
import com.gather.gather.domain.recruit.dto.RecruitParticipationResponse;
import com.gather.gather.domain.recruit.dto.RecruitUpdateRequest;
import com.gather.gather.domain.recruit.entity.MeetingRecruit;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipation;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import com.gather.gather.domain.recruit.entity.RecruitApplicantType;
import com.gather.gather.domain.recruit.entity.RecruitConfirmationStatus;
import com.gather.gather.domain.recruit.repository.MeetingRecruitParticipationRepository;
import com.gather.gather.domain.recruit.repository.MeetingRecruitRepository;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import com.gather.gather.global.util.DuplicateSubmissionGuard;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 내부 모집공고(RECRUIT) 작성·상세 및 참여신청.
 *
 * <p>권한 정책
 *
 * <ul>
 *   <li>작성·수정: 모임장(HOST)만. 단, 공고 기반 모임(volunteerPostingId != null)은 작성 불가(자유 모임의 팀장만 작성 가능)
 *   <li>상세 열람: {@code external=false}면 승인된 모임원만, {@code external=true}면 비로그인 포함 누구나
 *   <li>참여신청/취소: {@code external=false}면 승인된 모임원만, {@code external=true}면 로그인한 사용자 누구나. 신청 마감 전이고
 *       아직 확정(CONFIRMED)되지 않은 동안만 가능
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingRecruitService {

    private static final String POSTING_CREATED_MESSAGE = "[%s]에 새 봉사공고가 등록되었어요.";

    /** 현재 신청 인원(정원 계산용)에 포함되는 상태 - 취소·반려된 신청은 제외한다. */
    private static final Set<MeetingRecruitParticipationStatus> ACTIVE_STATUSES =
            EnumSet.of(
                    MeetingRecruitParticipationStatus.APPLIED,
                    MeetingRecruitParticipationStatus.CONFIRMED,
                    MeetingRecruitParticipationStatus.COMPLETED,
                    MeetingRecruitParticipationStatus.REVIEWED);

    private final PostRepository postRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final MeetingRecruitRepository meetingRecruitRepository;
    private final MeetingRecruitParticipationRepository participationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DuplicateSubmissionGuard duplicateSubmissionGuard;

    @Transactional
    public RecruitDetailResponse createRecruit(Long meetingId, RecruitCreateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        duplicateSubmissionGuard.guard("recruit:create:" + userId + ":" + meetingId);
        Meeting meeting = getMeeting(meetingId);
        requireHost(meetingId, userId);
        // RECRUIT 게시글은 자유 모임의 팀장만 작성할 수 있다. 공고 기반 모임(volunteerPostingId != null)은
        // 이미 원본 봉사공고가 있으므로 자체 모집공고를 별도로 만들 수 없다(프론트 UI 차단과 별개로 서버에서도 보장).
        if (meeting.isPostingBased()) {
            throw new BusinessException(ErrorCode.RECRUIT_POSTING_BASED_NOT_ALLOWED);
        }
        validateSchedule(
                request.activityStartAt(), request.activityEndAt(), request.applyDeadlineAt());
        Integer recognizedMinutes =
                resolveRecognizedMinutes(request.timeRecognized(), request.recognizedMinutes());

        User author = getUser(userId);
        Post post =
                postRepository.save(
                        Post.create(
                                meeting,
                                author,
                                request.title(),
                                request.content(),
                                PostType.RECRUIT,
                                request.maxParticipants()));

        MeetingRecruit recruit =
                meetingRecruitRepository.save(
                        MeetingRecruit.create(
                                post.getId(),
                                request.regionId(),
                                request.place(),
                                request.activityStartAt(),
                                request.activityEndAt(),
                                request.maxParticipants(),
                                request.timeRecognized(),
                                recognizedMinutes,
                                request.applyDeadlineAt(),
                                request.external(),
                                request.categories(),
                                request.participationCondition()));

        String message = POSTING_CREATED_MESSAGE.formatted(meeting.getName());
        eventPublisher.publishEvent(
                new MeetingPostNotificationRequestedEvent(
                        meeting.getId(),
                        post.getId(),
                        author.getId(),
                        NotificationType.MEETING_POSTING_CREATED,
                        message));

        return toDetail(post, recruit, meeting, author, 0, null, true, true, true);
    }

    @Transactional
    public RecruitDetailResponse updateRecruit(
            Long meetingId, Long postId, RecruitUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeeting(meetingId);
        Post post = getRecruitPost(meetingId, postId);
        if (!post.isAuthor(userId)) {
            throw new BusinessException(ErrorCode.POST_FORBIDDEN);
        }
        validateSchedule(
                request.activityStartAt(), request.activityEndAt(), request.applyDeadlineAt());
        Integer recognizedMinutes =
                resolveRecognizedMinutes(request.timeRecognized(), request.recognizedMinutes());

        MeetingRecruit recruit = getRecruitDetail(postId);
        long appliedCount =
                participationRepository.countByPostIdAndStatusIn(postId, ACTIVE_STATUSES);
        if (request.maxParticipants() < appliedCount) {
            throw new BusinessException(ErrorCode.RECRUIT_MAX_BELOW_APPLIED);
        }

        post.update(request.title(), request.content());
        recruit.update(
                request.regionId(),
                request.place(),
                request.activityStartAt(),
                request.activityEndAt(),
                request.maxParticipants(),
                request.timeRecognized(),
                recognizedMinutes,
                request.applyDeadlineAt(),
                request.external(),
                request.categories(),
                request.participationCondition());

        MeetingRecruitParticipationStatus status =
                participationRepository
                        .findByPostIdAndUserId(postId, userId)
                        .map(MeetingRecruitParticipation::getStatus)
                        .orElse(null);
        // 수정은 작성자 본인만 도달하므로 canEdit/canDelete 모두 true.
        return toDetail(
                post,
                recruit,
                meeting,
                post.getUser(),
                (int) appliedCount,
                status,
                true,
                true,
                true);
    }

    public RecruitDetailResponse getRecruit(Long meetingId, Long postId) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        Meeting meeting = getMeeting(meetingId);
        Post post = getRecruitPost(meetingId, postId);
        MeetingRecruit recruit = getRecruitDetail(postId);

        boolean member = userId != null && isApprovedMember(meetingId, userId);
        if (!recruit.isExternal() && !member) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }

        long appliedCount =
                participationRepository.countByPostIdAndStatusIn(postId, ACTIVE_STATUSES);
        MeetingRecruitParticipationStatus status =
                userId == null
                        ? null
                        : participationRepository
                                .findByPostIdAndUserId(postId, userId)
                                .map(MeetingRecruitParticipation::getStatus)
                                .orElse(null);
        boolean author = userId != null && post.isAuthor(userId);
        boolean host = userId != null && isHost(meetingId, userId);
        return toDetail(
                post,
                recruit,
                meeting,
                post.getUser(),
                (int) appliedCount,
                status,
                member,
                author,
                author || host);
    }

    @Transactional
    public RecruitParticipationResponse toggleParticipation(Long meetingId, Long postId) {
        Long userId = SecurityUtil.getCurrentUserId();
        getMeeting(meetingId);
        Post post = getRecruitPost(meetingId, postId);
        MeetingRecruit recruit = getRecruitDetail(postId);

        boolean member = isApprovedMember(meetingId, userId);
        if (!recruit.isExternal() && !member) {
            throw new BusinessException(ErrorCode.MEETING_MEMBER_REQUIRED);
        }
        RecruitApplicantType applicantType =
                member ? RecruitApplicantType.MEMBER : RecruitApplicantType.EXTERNAL;
        LocalDateTime now = LocalDateTime.now();

        Optional<MeetingRecruitParticipation> existingOpt =
                participationRepository.findByPostIdAndUserId(postId, userId);

        if (existingOpt.isEmpty()) {
            requireOpen(recruit, now);
            requireCapacity(postId, recruit);
            MeetingRecruitParticipation saved =
                    participationRepository.save(
                            MeetingRecruitParticipation.apply(postId, userId, applicantType));
            long count = participationRepository.countByPostIdAndStatusIn(postId, ACTIVE_STATUSES);
            return new RecruitParticipationResponse(
                    saved.getId(),
                    MeetingRecruitParticipationStatus.APPLIED,
                    RecruitParticipationAction.CANCEL,
                    (int) count);
        }

        MeetingRecruitParticipation participation = existingOpt.get();
        return switch (participation.getStatus()) {
            case APPLIED -> {
                requireOpen(recruit, now);
                participation.cancel();
                long count =
                        participationRepository.countByPostIdAndStatusIn(postId, ACTIVE_STATUSES);
                boolean open = isOpen(recruit, now);
                yield new RecruitParticipationResponse(
                        participation.getId(),
                        MeetingRecruitParticipationStatus.CANCELLED,
                        open ? RecruitParticipationAction.APPLY : RecruitParticipationAction.NONE,
                        (int) count);
            }
            case CANCELLED -> {
                requireOpen(recruit, now);
                requireCapacity(postId, recruit);
                participation.reapply(applicantType);
                long count =
                        participationRepository.countByPostIdAndStatusIn(postId, ACTIVE_STATUSES);
                yield new RecruitParticipationResponse(
                        participation.getId(),
                        MeetingRecruitParticipationStatus.APPLIED,
                        RecruitParticipationAction.CANCEL,
                        (int) count);
            }
            case REJECTED -> throw new BusinessException(ErrorCode.RECRUIT_REAPPLY_NOT_ALLOWED);
            case CONFIRMED, COMPLETED, REVIEWED ->
                    throw new BusinessException(ErrorCode.RECRUIT_CONFIRMED_LOCKED);
        };
    }

    private void requireOpen(MeetingRecruit recruit, LocalDateTime now) {
        if (recruit.getConfirmationStatus() == RecruitConfirmationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.RECRUIT_CONFIRMED_LOCKED);
        }
        if (!recruit.isApplicationOpen(now)) {
            throw new BusinessException(ErrorCode.RECRUIT_APPLICATION_CLOSED);
        }
    }

    private boolean isOpen(MeetingRecruit recruit, LocalDateTime now) {
        return recruit.getConfirmationStatus() == RecruitConfirmationStatus.UNCONFIRMED
                && recruit.isApplicationOpen(now);
    }

    private void requireCapacity(Long postId, MeetingRecruit recruit) {
        long activeCount =
                participationRepository.countByPostIdAndStatusIn(postId, ACTIVE_STATUSES);
        if (activeCount >= recruit.getMaxParticipants()) {
            throw new BusinessException(ErrorCode.RECRUIT_CAPACITY_EXCEEDED);
        }
    }

    private RecruitParticipationAction resolveAction(
            MeetingRecruitParticipationStatus status,
            MeetingRecruit recruit,
            boolean eligible,
            LocalDateTime now) {
        boolean open = eligible && isOpen(recruit, now);
        if (status == null) {
            return open ? RecruitParticipationAction.APPLY : RecruitParticipationAction.NONE;
        }
        return switch (status) {
            case APPLIED ->
                    open ? RecruitParticipationAction.CANCEL : RecruitParticipationAction.NONE;
            case CANCELLED ->
                    open ? RecruitParticipationAction.APPLY : RecruitParticipationAction.NONE;
            case REJECTED, CONFIRMED, COMPLETED, REVIEWED -> RecruitParticipationAction.NONE;
        };
    }

    private void validateSchedule(
            LocalDateTime activityStartAt,
            LocalDateTime activityEndAt,
            LocalDateTime applyDeadlineAt) {
        if (!activityStartAt.isBefore(activityEndAt)) {
            throw new BusinessException(ErrorCode.RECRUIT_INVALID_SCHEDULE);
        }
        if (applyDeadlineAt.isAfter(activityStartAt)) {
            throw new BusinessException(ErrorCode.RECRUIT_INVALID_DEADLINE);
        }
    }

    private Integer resolveRecognizedMinutes(boolean timeRecognized, Integer recognizedMinutes) {
        if (timeRecognized && recognizedMinutes == null) {
            throw new BusinessException(ErrorCode.RECRUIT_RECOGNIZED_MINUTES_REQUIRED);
        }
        return timeRecognized ? recognizedMinutes : null;
    }

    private RecruitDetailResponse toDetail(
            Post post,
            MeetingRecruit recruit,
            Meeting meeting,
            User author,
            int appliedCount,
            MeetingRecruitParticipationStatus participationStatus,
            boolean eligible,
            boolean canEdit,
            boolean canDelete) {
        LocalDateTime now = LocalDateTime.now();
        String regionName =
                recruit.getRegionId() == null
                        ? null
                        : regionRepository
                                .findById(recruit.getRegionId())
                                .map(r -> r.getName())
                                .orElse(null);
        return new RecruitDetailResponse(
                post.getId(),
                meeting.getId(),
                meeting.getName(),
                post.getTitle(),
                post.getContent(),
                author.getId(),
                author.getNickname(),
                recruit.getRegionId(),
                regionName,
                recruit.getPlace(),
                recruit.getActivityStartAt(),
                recruit.getActivityEndAt(),
                recruit.getApplyDeadlineAt(),
                recruit.getMaxParticipants(),
                appliedCount,
                recruit.getCategories(),
                recruit.isTimeRecognized(),
                recruit.getRecognizedMinutes(),
                recruit.isExternal(),
                recruit.getParticipationCondition(),
                post.getLikeCount(),
                post.getCommentCount(),
                isOpen(recruit, now),
                appliedCount >= recruit.getMaxParticipants(),
                recruit.getConfirmationStatus(),
                recruit.getConfirmedAt(),
                participationStatus,
                resolveAction(participationStatus, recruit, eligible, now),
                canEdit,
                canDelete,
                post.getCreatedAt(),
                post.getUpdatedAt());
    }

    private Meeting getMeeting(Long meetingId) {
        return meetingRepository
                .findByIdAndDeletedAtIsNull(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
    }

    /** RECRUIT 유형이면서 해당 모임에 속한 게시글을 조회한다. */
    private Post getRecruitPost(Long meetingId, Long postId) {
        Post post =
                postRepository
                        .findByIdFetchUser(postId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RECRUIT_NOT_FOUND));
        if (!post.getMeeting().getId().equals(meetingId) || post.getType() != PostType.RECRUIT) {
            throw new BusinessException(ErrorCode.RECRUIT_NOT_FOUND);
        }
        return post;
    }

    private MeetingRecruit getRecruitDetail(Long postId) {
        return meetingRecruitRepository
                .findByPostId(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECRUIT_NOT_FOUND));
    }

    private User getUser(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private boolean isApprovedMember(Long meetingId, Long userId) {
        return meetingMemberRepository.existsByMeeting_IdAndUser_IdAndStatus(
                meetingId, userId, MeetingMemberStatus.APPROVED);
    }

    private void requireHost(Long meetingId, Long userId) {
        MeetingMember membership =
                meetingMemberRepository
                        .findByMeeting_IdAndUser_IdAndStatus(
                                meetingId, userId, MeetingMemberStatus.APPROVED)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.MEETING_MEMBER_REQUIRED));
        if (membership.getRole() != MeetingMemberRole.HOST) {
            throw new BusinessException(ErrorCode.RECRUIT_HOST_ONLY);
        }
    }

    private boolean isHost(Long meetingId, Long userId) {
        return meetingMemberRepository
                .findByMeeting_IdAndUser_IdAndStatus(
                        meetingId, userId, MeetingMemberStatus.APPROVED)
                .map(member -> member.getRole() == MeetingMemberRole.HOST)
                .orElse(false);
    }
}
