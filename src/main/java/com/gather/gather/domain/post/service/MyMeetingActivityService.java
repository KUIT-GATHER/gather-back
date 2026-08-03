package com.gather.gather.domain.post.service;

import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.post.dto.MyMeetingActivitySummaryResponse;
import com.gather.gather.domain.post.dto.PostSummaryResponse;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.repository.PostCommentRepository;
import com.gather.gather.domain.post.repository.PostRepository;
import com.gather.gather.domain.recruit.dto.MyAppliedRecruitResponse;
import com.gather.gather.domain.recruit.repository.MeetingRecruitParticipationRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 내부 "나의 활동" 탭 조회. 가입자 전용이며, 작성한 게시글·댓글 단 게시글 목록과 탭 요약(개수)을 제공한다.
 *
 * <p>"내가 신청한 봉사"는 모집공고(RECRUIT) 참여신청 기능과 함께 다음 라운드에서 추가한다.
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
