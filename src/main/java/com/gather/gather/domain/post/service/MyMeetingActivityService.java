package com.gather.gather.domain.post.service;

import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.post.dto.MyMeetingActivitySummaryResponse;
import com.gather.gather.domain.post.dto.PostSummaryResponse;
import com.gather.gather.domain.post.dto.ReviewableActivityResponse;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.enums.ReviewSourceType;
import com.gather.gather.domain.post.repository.PostCommentRepository;
import com.gather.gather.domain.post.repository.PostRepository;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.recruit.dto.MyAppliedRecruitResponse;
import com.gather.gather.domain.recruit.dto.ReviewableRecruitActivity;
import com.gather.gather.domain.recruit.repository.MeetingRecruitParticipationRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 내 "나의 활동" 탭 조회. 가입자 전용이며, 작성한 게시글·댓글단 게시글 목록과 각 요약(개수)을 제공한다.
 *
 * <p>"닫기 신청 봉사"는 모집공고(RECRUIT) 참여신청 기능과 함께 이 클래스에도 추가했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyMeetingActivityService {

    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostSummaryAssembler summaryAssembler;
    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final MeetingRecruitParticipationRepository recruitParticipationRepository;
    private final PostingParticipationRepository postingParticipationRepository;
    private final PostingRepository postingRepository;

    public PageResponse<PostSummaryResponse> getMyPosts(Long meetingId, Pageable pageable) {
        Long userId = requireApprovedMember(meetingId);
        Page<Post> page = postRepository.findMyPosts(meetingId, userId, pageable);
        return summaryAssembler.assemble(page, userId);
    }

    public PageResponse<PostSummaryResponse> getMyCommentedPosts(
            Long meetingId, Pageable pageable) {
        Long userId = requireApprovedMember(meetingId);
        Page<Post> page = postRepository.findMyCommentedPosts(meetingId, userId, pageable);
        return summaryAssembler.assemble(page, userId);
    }

    public MyMeetingActivitySummaryResponse getActivitySummary(Long meetingId) {
        Long userId = requireApprovedMember(meetingId);
        long writtenPostCount =
                postRepository.countByMeeting_IdAndUser_IdAndDeletedAtIsNull(meetingId, userId);
        long commentedPostCount = postCommentRepository.countCommentedPosts(meetingId, userId);
        long appliedRecruitCount =
                recruitParticipationRepository.countMyAppliedRecruits(userId, meetingId);
        return new MyMeetingActivitySummaryResponse(
                writtenPostCount, commentedPostCount, appliedRecruitCount);
    }

    public PageResponse<MyAppliedRecruitResponse> getMyAppliedRecruits(
            Long meetingId, Pageable pageable) {
        Long userId = requireApprovedMember(meetingId);
        return PageResponse.from(
                recruitParticipationRepository.findMyAppliedRecruits(userId, meetingId, pageable));
    }

    /** 활동 후기 작성 가능 활동 조회 - POSTING(연결 공고 완료) + MEETING_RECRUIT(모집공고 참석 완료) 출처를 함께 반환한다. */
    public List<ReviewableActivityResponse> getReviewableActivities(Long meetingId) {
        Long userId = requireApprovedMember(meetingId);
        Meeting meeting =
                meetingRepository
                        .findByIdAndDeletedAtIsNull(meetingId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));

        List<ReviewableActivityResponse> activities = new ArrayList<>();
        addReviewablePosting(meeting, userId, activities);
        recruitParticipationRepository.findReviewableActivities(userId, meetingId).stream()
                .map(this::toRecruitActivity)
                .forEach(activities::add);
        return activities;
    }

    private void addReviewablePosting(
            Meeting meeting, Long userId, List<ReviewableActivityResponse> activities) {
        Long volunteerPostingId = meeting.getVolunteerPostingId();
        if (volunteerPostingId == null) {
            return;
        }
        postingParticipationRepository
                .findByUserIdAndPostingId(userId, volunteerPostingId)
                .filter(p -> p.getStatus() == PostingParticipationStatus.COMPLETED)
                .flatMap(p -> postingRepository.findById(volunteerPostingId))
                .map(this::toPostingActivity)
                .ifPresent(activities::add);
    }

    private ReviewableActivityResponse toPostingActivity(Posting posting) {
        LocalDate startDate =
                posting.getActStartDate() != null
                        ? posting.getActStartDate()
                        : posting.getActivityDate();
        LocalDate endDate = posting.getActEndDate() != null ? posting.getActEndDate() : startDate;
        return new ReviewableActivityResponse(
                ReviewSourceType.POSTING,
                posting.getId(),
                posting.getTitle(),
                startDate.atStartOfDay(),
                endDate.atTime(LocalTime.of(23, 59, 59)));
    }

    private ReviewableActivityResponse toRecruitActivity(ReviewableRecruitActivity activity) {
        return new ReviewableActivityResponse(
                ReviewSourceType.MEETING_RECRUIT,
                activity.postId(),
                activity.title(),
                activity.activityStartAt(),
                activity.activityEndAt());
    }

    private Long requireApprovedMember(Long meetingId) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (meetingRepository.findByIdAndDeletedAtIsNull(meetingId).isEmpty()) {
            throw new BusinessException(ErrorCode.MEETING_NOT_FOUND);
        }
        boolean member =
                meetingMemberRepository.existsByMeeting_IdAndUser_IdAndStatus(
                        meetingId, userId, MeetingMemberStatus.APPROVED);
        if (!member) {
            throw new BusinessException(ErrorCode.MEETING_MEMBER_REQUIRED);
        }
        return userId;
    }
}
