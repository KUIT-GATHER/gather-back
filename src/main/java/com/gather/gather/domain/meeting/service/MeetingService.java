package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.event.BadgeAwardRequestedEvent;
import com.gather.gather.domain.badge.event.MeetingCompletedEvent;
import com.gather.gather.domain.meeting.dto.MeetingCreateRequest;
import com.gather.gather.domain.meeting.dto.MeetingDetailResponse;
import com.gather.gather.domain.meeting.dto.MeetingJoinRequestResponse;
import com.gather.gather.domain.meeting.dto.MeetingJoinResponse;
import com.gather.gather.domain.meeting.dto.MeetingResponse;
import com.gather.gather.domain.meeting.dto.PostingMeetingResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberRole;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingBookmarkRepository;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.notification.event.MeetingJoinResultNotificationRequestedEvent;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.recruit.repository.MeetingRecruitParticipationRepository;
import com.gather.gather.domain.posting.service.RegionNameResolver;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.RecognizedMinutesValidator;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingService {

    private static final Set<String> SORTABLE_PROPERTIES =
            Set.of(
                    "id",
                    "name",
                    "currentMemberCount",
                    "maxMember",
                    "regionId",
                    "status",
                    "deadline",
                    "activityStartAt",
                    "activityEndAt",
                    "createdAt",
                    "updatedAt");

    private final MeetingRepository meetingRepository;
    private final MeetingBookmarkRepository meetingBookmarkRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final RegionNameResolver regionNameResolver;
    private final PostingRepository postingRepository;
    private final MeetingRecruitParticipationRepository meetingRecruitParticipationRepository;
    private final MeetingSearchLogService meetingSearchLogService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MeetingResponse createMeeting(MeetingCreateRequest request) {
        validateMeetingTime(
                request.deadline(),
                request.activityStartAt(),
                request.activityEndAt(),
                request.volunteerPostingId());
        validateRegionExists(request.regionId());
        Set<PostingCategory> categories = resolveCategories(request);
        Long userId = SecurityUtil.getCurrentUserId();
        User host = getUser(userId);

        Meeting meeting =
                Meeting.create(
                        request.name(),
                        request.description(),
                        request.maxMember(),
                        request.deadline(),
                        request.memo(),
                        categories,
                        request.regionId(),
                        host,
                        request.participationCondition(),
                        request.volunteerPostingId(),
                        request.activityStartAt(),
                        request.activityEndAt());

        Meeting savedMeeting = meetingRepository.save(meeting);

        MeetingMember hostMember = MeetingMember.createHost(host, savedMeeting);
        meetingMemberRepository.save(hostMember);
        eventPublisher.publishEvent(new BadgeAwardRequestedEvent(userId, BadgeType.TEAM_CREATED));

        String regionName =
                regionRepository
                        .findById(savedMeeting.getRegionId())
                        .map(Region::getName)
                        .orElse(null);
        return MeetingResponse.from(savedMeeting, resolveDisplayStatus(savedMeeting), regionName);
    }

    public PageResponse<MeetingResponse> getMeetings(
            String keyword,
            Long regionId,
            PostingCategory category,
            MeetingStatus status,
            LocalDate activityStartDate,
            LocalDate activityEndDate,
            Boolean postingBasedFirst,
            Pageable pageable) {
        validateSort(pageable.getSort());

        // 지역: 상위(시·도) 선택 시 하위 시군구·읍면동까지 포함(봉사공고와 동일 정책).
        boolean hasRegionFilter = regionId != null;
        List<Long> regionIds =
                hasRegionFilter ? regionRepository.findIdsIncludingChildren(regionId) : List.of();
        // hasRegionFilter=false면 무시되지만 empty-IN 방지를 위해 더미값을 넣는다.
        // regionId가 유효하지 않아 빈 리스트면 IN(-1)로 결과 0건이 된다.
        List<Long> regionIdParam = regionIds.isEmpty() ? List.of(-1L) : regionIds;

        boolean recruitingOnly = status == MeetingStatus.RECRUITING;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime activityStartAt =
                activityStartDate == null ? null : activityStartDate.atStartOfDay();
        LocalDateTime activityEndAt =
                activityEndDate == null ? null : activityEndDate.atTime(LocalTime.MAX);

        Page<Meeting> meetings =
                meetingRepository.searchMeetings(
                        keyword,
                        hasRegionFilter,
                        regionIdParam,
                        category,
                        status,
                        recruitingOnly,
                        now,
                        activityStartAt,
                        activityEndAt,
                        postingBasedFirst,
                        pageable);
        Map<Long, String> regionNames =
                regionNameResolver.resolve(regionIdsOf(meetings.getContent()));

        Page<MeetingResponse> responses =
                meetings.map(
                        meeting ->
                                MeetingResponse.from(
                                        meeting,
                                        resolveDisplayStatus(meeting),
                                        regionNames.get(meeting.getRegionId())));

        logSearchKeywordSafely(keyword);
        return PageResponse.from(responses);
    }

    private List<Long> regionIdsOf(List<Meeting> meetings) {
        return meetings.stream().map(Meeting::getRegionId).toList();
    }

    public PageResponse<PostingMeetingResponse> getMeetingsByPosting(
            Long postingId, Pageable pageable) {
        validateSort(pageable.getSort());

        if (!postingRepository.existsById(postingId)) {
            throw new BusinessException(ErrorCode.POSTING_NOT_FOUND);
        }

        Page<Meeting> meetings =
                meetingRepository.findAllByVolunteerPostingIdAndDeletedAtIsNull(
                        postingId, pageable);
        Map<Long, MeetingMemberRole> membershipRoles =
                getMembershipRoles(SecurityUtil.getCurrentUserIdOrNull(), meetings.getContent());

        Page<PostingMeetingResponse> responses =
                meetings.map(
                        meeting -> {
                            MeetingMemberRole role = membershipRoles.get(meeting.getId());
                            return PostingMeetingResponse.from(
                                    meeting,
                                    resolveDisplayStatus(meeting),
                                    role != null,
                                    role == MeetingMemberRole.HOST);
                        });

        return PageResponse.from(responses);
    }

    private Map<Long, MeetingMemberRole> getMembershipRoles(Long userId, List<Meeting> meetings) {
        if (userId == null || meetings.isEmpty()) {
            return Map.of();
        }

        List<Long> meetingIds = meetings.stream().map(Meeting::getId).toList();
        return meetingMemberRepository
                .findAllByUserIdAndStatusAndMeetingIdInFetchMeeting(
                        userId, MeetingMemberStatus.APPROVED, meetingIds)
                .stream()
                .collect(
                        Collectors.toMap(
                                member -> member.getMeeting().getId(), MeetingMember::getRole));
    }

    public MeetingDetailResponse getMeeting(Long meetingId) {
        Meeting meeting = getMeetingEntity(meetingId);
        return MeetingDetailResponse.from(
                meeting, resolveDisplayStatus(meeting), isBookmarkedByCurrentUser(meetingId));
    }

    /** 인증이 선택적인 상세 조회이므로 비로그인 사용자는 항상 false를 받는다. */
    private boolean isBookmarkedByCurrentUser(Long meetingId) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        return userId != null
                && meetingBookmarkRepository.existsByUserIdAndMeetingId(userId, meetingId);
    }

    @Transactional
    public MeetingJoinResponse joinMeeting(Long meetingId) {
        Long userId = SecurityUtil.getCurrentUserId();
        User user = getUser(userId);
        Meeting meeting = getMeetingEntityForUpdate(meetingId);

        validateJoinableMeeting(meeting, userId);

        MeetingMember member =
                meetingMemberRepository
                        .findByMeeting_IdAndUser_Id(meetingId, userId)
                        .map(
                                existingMember -> {
                                    existingMember.requestAgain();
                                    return existingMember;
                                })
                        .orElseGet(() -> MeetingMember.createMember(user, meeting));

        try {
            MeetingMember savedMember = meetingMemberRepository.saveAndFlush(member);
            return MeetingJoinResponse.from(savedMember);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.MEETING_JOIN_REQUEST_DUPLICATE);
        }
    }

    public List<MeetingJoinRequestResponse> getPendingJoinRequests(Long meetingId) {
        Meeting meeting = getMeetingEntity(meetingId);
        validateHost(meeting, SecurityUtil.getCurrentUserId());

        return meetingMemberRepository.findPendingByMeetingIdFetchUser(meetingId).stream()
                .map(MeetingJoinRequestResponse::from)
                .toList();
    }

    @Transactional
    public MeetingJoinRequestResponse approveJoinRequest(Long meetingId, Long joinRequestId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeetingEntityForUpdate(meetingId);
        validateHost(meeting, userId);
        validateApprovableMeeting(meeting);

        MeetingMember member = getPendingJoinRequestForUpdate(meetingId, joinRequestId);
        member.approve();
        meeting.increaseMemberCount();
        if (member.getRole() == MeetingMemberRole.MEMBER) {
            eventPublisher.publishEvent(
                    new BadgeAwardRequestedEvent(
                            member.getUser().getId(), BadgeType.FIRST_TEAM_JOIN));
        }
        eventPublisher.publishEvent(
                new MeetingJoinResultNotificationRequestedEvent(
                        member.getUser().getId(), meeting.getId(), meeting.getName(), true));
        return MeetingJoinRequestResponse.from(member);
    }

    @Transactional
    public MeetingJoinRequestResponse rejectJoinRequest(Long meetingId, Long joinRequestId) {
        Meeting meeting = getMeetingEntityForUpdate(meetingId);
        validateHost(meeting, SecurityUtil.getCurrentUserId());

        MeetingMember member = getPendingJoinRequestForUpdate(meetingId, joinRequestId);
        member.reject();

        eventPublisher.publishEvent(
                new MeetingJoinResultNotificationRequestedEvent(
                        member.getUser().getId(), meeting.getId(), meeting.getName(), false));
        return MeetingJoinRequestResponse.from(member);
    }

    /**
     * 모임을 해산한다(모임장 전용, 소프트 삭제).
     *
     * <p>아직 활동일이 지나지 않은 모집공고에 확정(CONFIRMED)된 참가자가 있으면 해산할 수 없다(진행 예정 활동 보호). 확정된 참가자가 없거나 이미 활동이
     * 끝난 경우에만 해산 가능하다. {@code Meeting.deletedAt}만 채우면 된다 — 이 레포의 모임 관련 조회는 전부 {@code
     * findByIdAndDeletedAtIsNull}류를 통해 상위 모임 존재를 먼저 확인하므로, 게시글·멤버·북마크·나의 모임 목록 등 하위 조회도 이 시점부터 함께
     * 막힌다. 완료된 개인 봉사 기록·인정시간·후기·뱃지는 모임 삭제와 무관하게 그대로 유지된다. 되돌릴 수 없다.
     */
    @Transactional
    public void disbandMeeting(Long meetingId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeetingEntityForUpdate(meetingId);
        validateHost(meeting, userId);

        if (meetingRecruitParticipationRepository.existsConfirmedParticipantWithUpcomingActivity(
                meetingId, LocalDate.now())) {
            throw new BusinessException(ErrorCode.MEETING_DISBAND_HAS_CONFIRMED_PARTICIPANTS);
        }

        meeting.delete();
    }

    /** 모임(그룹) 봉사 완료 판정: 모임장이 직접 완료 처리한다(개인 봉사는 본인이 활동종료일 이후 완료 처리한다). */
    @Transactional
    public void completeMeeting(Long meetingId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeetingEntityForUpdate(meetingId);
        validateHost(meeting, userId);

        if (meeting.getStatus() == MeetingStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.MEETING_ALREADY_COMPLETED);
        }
        if (meeting.hasActivityPeriod() && !meeting.isActivityEnded(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.MEETING_COMPLETE_NOT_ALLOWED);
        }

        meeting.complete();
        eventPublisher.publishEvent(new MeetingCompletedEvent(meetingId));
    }

    /** 모임 완료 처리 이후, 승인된 멤버 본인이 직접 인정시간을 입력한다(분 단위, 1회만 입력 가능). */
    @Transactional
    public void submitMemberHours(Long meetingId, Integer recognizedMinutes) {
        RecognizedMinutesValidator.validate(recognizedMinutes);

        Long userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeetingEntity(meetingId);
        if (meeting.getStatus() != MeetingStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.MEETING_HOURS_NOT_ALLOWED);
        }

        MeetingMember member =
                meetingMemberRepository
                        .findByMeeting_IdAndUser_IdAndStatus(
                                meetingId, userId, MeetingMemberStatus.APPROVED)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.MEETING_MEMBER_REQUIRED));

        if (member.getRecognizedMinutes() != null) {
            throw new BusinessException(ErrorCode.MEETING_HOURS_ALREADY_SUBMITTED);
        }

        member.submitRecognizedMinutes(recognizedMinutes);
    }

    public List<MeetingResponse> getMyMeetings() {
        Long userId = SecurityUtil.getCurrentUserId();

        List<MeetingMember> members =
                meetingMemberRepository.findAllByUserIdAndStatusFetchMeeting(
                        userId, MeetingMemberStatus.APPROVED);
        Map<Long, String> regionNames =
                regionNameResolver.resolve(
                        members.stream().map(member -> member.getMeeting().getRegionId()).toList());

        return members.stream()
                .map(
                        member ->
                                MeetingResponse.from(
                                        member.getMeeting(),
                                        resolveDisplayStatus(member.getMeeting()),
                                        regionNames.get(member.getMeeting().getRegionId()),
                                        member.getRole(), // ← HOST/MEMBER
                                        member.getRecognizedMinutes()))
                .toList();
    }

    private void logSearchKeywordSafely(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }

        try {
            meetingSearchLogService.log(keyword);
        } catch (RuntimeException e) {
            log.warn("모임 검색어 로깅 실패. keyword 길이={}", keyword.length(), e);
        }
    }

    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        }
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

    private Meeting getMeetingEntityForUpdate(Long meetingId) {
        return meetingRepository
                .findByIdAndDeletedAtIsNullForUpdate(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
    }

    private void validateJoinableMeeting(Meeting meeting, Long userId) {
        MeetingStatus displayStatus = resolveDisplayStatus(meeting);

        if (displayStatus != MeetingStatus.RECRUITING) {
            throw new BusinessException(ErrorCode.MEETING_CLOSED);
        }

        if (meeting.isFull()) {
            throw new BusinessException(ErrorCode.MEETING_FULL);
        }

        meetingMemberRepository
                .findByMeeting_IdAndUser_Id(meeting.getId(), userId)
                .filter(
                        member ->
                                member.getStatus() == MeetingMemberStatus.APPROVED
                                        || member.getStatus() == MeetingMemberStatus.PENDING)
                .ifPresent(
                        member -> {
                            throw new BusinessException(
                                    member.getStatus() == MeetingMemberStatus.PENDING
                                            ? ErrorCode.MEETING_JOIN_REQUEST_DUPLICATE
                                            : ErrorCode.MEETING_ALREADY_JOINED);
                        });
    }

    private MeetingMember getPendingJoinRequestForUpdate(Long meetingId, Long joinRequestId) {
        return meetingMemberRepository
                .findPendingByIdAndMeetingIdForUpdate(joinRequestId, meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_JOIN_REQUEST_NOT_FOUND));
    }

    private void validateApprovableMeeting(Meeting meeting) {
        if (resolveDisplayStatus(meeting) != MeetingStatus.RECRUITING) {
            throw new BusinessException(ErrorCode.MEETING_CLOSED);
        }
    }

    private void validateHost(Meeting meeting, Long userId) {
        if (!meeting.getHost().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.MEETING_HOST_ONLY);
        }
    }

    private Set<PostingCategory> resolveCategories(MeetingCreateRequest request) {
        if (request.volunteerPostingId() != null) {
            Posting posting =
                    postingRepository
                            .findById(request.volunteerPostingId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.POSTING_NOT_FOUND));

            return Set.of(posting.getCategory());
        }

        if (request.categories() == null || request.categories().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (request.categories().size() > 3) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        return Set.copyOf(request.categories());
    }

    private void validateRegionExists(Long regionId) {
        if (!regionRepository.existsById(regionId)) {
            throw new BusinessException(ErrorCode.REGION_NOT_FOUND);
        }
    }

    private void validateMeetingTime(
            LocalDateTime deadline,
            LocalDateTime activityStartAt,
            LocalDateTime activityEndAt,
            Long volunteerPostingId) {
        boolean postingBasedMeeting = volunteerPostingId != null;
        boolean activityPeriodMissing = activityStartAt == null && activityEndAt == null;

        if (activityPeriodMissing) {
            if (postingBasedMeeting) {
                throw new BusinessException(ErrorCode.INVALID_MEETING_TIME);
            }
            return;
        }

        if (activityStartAt == null || activityEndAt == null) {
            throw new BusinessException(ErrorCode.INVALID_MEETING_TIME);
        }

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
