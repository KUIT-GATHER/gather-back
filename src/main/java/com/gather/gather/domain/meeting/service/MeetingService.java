package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
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
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                    "category",
                    "status",
                    "deadline",
                    "activityStartAt",
                    "activityEndAt",
                    "createdAt",
                    "updatedAt");

    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final PostingRepository postingRepository;
    private final MeetingSearchLogService meetingSearchLogService;

    @Transactional
    public MeetingResponse createMeeting(MeetingCreateRequest request) {
        validateMeetingTime(request.deadline(), request.activityStartAt(), request.activityEndAt());
        validateRegionExists(request.regionId());
        PostingCategory category = resolveCategory(request);

        Long userId = SecurityUtil.getCurrentUserId();
        User host = getUser(userId);

        Meeting meeting =
                Meeting.create(
                        request.name(),
                        request.description(),
                        request.maxMember(),
                        request.deadline(),
                        request.memo(),
                        category,
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

    public PageResponse<MeetingResponse> getMeetings(
            String keyword,
            Long regionId,
            PostingCategory category,
            MeetingStatus status,
            Pageable pageable) {
        validateSort(pageable.getSort());

        Page<MeetingResponse> responses =
                meetingRepository
                        .searchMeetings(keyword, regionId, category, status, pageable)
                        .map(
                                meeting ->
                                        MeetingResponse.from(
                                                meeting, resolveDisplayStatus(meeting)));

        logSearchKeywordSafely(keyword);

        return PageResponse.from(responses);
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
        return MeetingDetailResponse.from(meeting, resolveDisplayStatus(meeting));
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
        return MeetingJoinRequestResponse.from(member);
    }

    @Transactional
    public MeetingJoinRequestResponse rejectJoinRequest(Long meetingId, Long joinRequestId) {
        Meeting meeting = getMeetingEntityForUpdate(meetingId);
        validateHost(meeting, SecurityUtil.getCurrentUserId());

        MeetingMember member = getPendingJoinRequestForUpdate(meetingId, joinRequestId);
        member.reject();
        return MeetingJoinRequestResponse.from(member);
    }

    public List<MeetingResponse> getMyMeetings() {
        Long userId = SecurityUtil.getCurrentUserId();

        return meetingMemberRepository
                .findAllByUserIdAndStatusFetchMeeting(userId, MeetingMemberStatus.APPROVED)
                .stream()
                .map(MeetingMember::getMeeting)
                .map(meeting -> MeetingResponse.from(meeting, resolveDisplayStatus(meeting)))
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

    private PostingCategory resolveCategory(MeetingCreateRequest request) {
        if (request.volunteerPostingId() != null) {
            Posting posting =
                    postingRepository
                            .findById(request.volunteerPostingId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.POSTING_NOT_FOUND));

            return posting.getCategory();
        }

        if (request.category() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        return request.category();
    }

    private void validateRegionExists(Long regionId) {
        if (!regionRepository.existsById(regionId)) {
            throw new BusinessException(ErrorCode.REGION_NOT_FOUND);
        }
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
