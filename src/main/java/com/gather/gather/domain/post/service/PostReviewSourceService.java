package com.gather.gather.domain.post.service;

import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.enums.ReviewSourceType;
import com.gather.gather.domain.post.repository.PostRepository;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipation;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import com.gather.gather.domain.recruit.repository.MeetingRecruitParticipationRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활동 후기(REVIEW 게시글)와 실제 완료된 활동 참여 기록을 연결한다.
 *
 * <p>완료(COMPLETED) 상태의 참여만 후기 근거로 쓸 수 있고, 연결에 성공하면 참여 상태를 REVIEWED로 바꿔 "참여 기록 하나당 활성 후기 1개"를
 * 강제한다(같은 참여로 다시 후기를 쓰려 하면 상태가 COMPLETED가 아니어서 막힌다). 후기가 삭제되면 다시 COMPLETED로 되돌려 재작성을 허용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostReviewSourceService {

    private final PostingParticipationRepository postingParticipationRepository;
    private final MeetingRecruitParticipationRepository meetingRecruitParticipationRepository;
    private final PostRepository postRepository;

    /** REVIEW 게시글 작성 시 호출한다. 검증 실패 시 예외를 던지고, 성공하면 게시글에 출처를 기록하고 참여를 REVIEWED로 전환한다. */
    @Transactional
    public void linkAndMarkReviewed(
            Post post, Meeting meeting, Long userId, ReviewSourceType type, Long sourceId) {
        if (type == null || sourceId == null) {
            throw new BusinessException(ErrorCode.POST_REVIEW_SOURCE_REQUIRED);
        }

        if (type == ReviewSourceType.POSTING) {
            linkPosting(meeting, userId, sourceId);
        } else {
            linkMeetingRecruit(meeting, userId, sourceId);
        }
        post.linkReviewSource(type, sourceId);
    }

    /** REVIEW 게시글이 삭제될 때 호출한다. 연결된 활동이 있으면 REVIEWED → COMPLETED로 되돌려 재작성을 허용한다. */
    @Transactional
    public void unlinkOnDelete(Post post) {
        ReviewSourceType type = post.getReviewSourceType();
        Long sourceId = post.getReviewSourceId();
        if (type == null || sourceId == null) {
            return;
        }
        Long userId = post.getUser().getId();
        if (type == ReviewSourceType.POSTING) {
            postingParticipationRepository
                    .findByUserIdAndPostingId(userId, sourceId)
                    .filter(p -> p.getStatus() == PostingParticipationStatus.REVIEWED)
                    .ifPresent(PostingParticipation::unreview);
        } else {
            meetingRecruitParticipationRepository
                    .findByPostIdAndUserId(sourceId, userId)
                    .filter(p -> p.getStatus() == MeetingRecruitParticipationStatus.REVIEWED)
                    .ifPresent(MeetingRecruitParticipation::unreview);
        }
    }

    private void linkPosting(Meeting meeting, Long userId, Long postingId) {
        if (meeting.getVolunteerPostingId() == null
                || !meeting.getVolunteerPostingId().equals(postingId)) {
            throw new BusinessException(ErrorCode.POST_REVIEW_ACTIVITY_NOT_FOUND);
        }
        PostingParticipation participation =
                postingParticipationRepository
                        .findByUserIdAndPostingId(userId, postingId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.POST_REVIEW_ACTIVITY_NOT_FOUND));
        requireReviewable(
                participation.getStatus() == PostingParticipationStatus.REVIEWED,
                participation.getStatus() == PostingParticipationStatus.COMPLETED);
        participation.review();
    }

    private void linkMeetingRecruit(Meeting meeting, Long userId, Long recruitPostId) {
        Post recruitPost =
                postRepository
                        .findByIdFetchUser(recruitPostId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.POST_REVIEW_ACTIVITY_NOT_FOUND));
        if (!recruitPost.getMeeting().getId().equals(meeting.getId())) {
            throw new BusinessException(ErrorCode.POST_REVIEW_ACTIVITY_NOT_FOUND);
        }
        MeetingRecruitParticipation participation =
                meetingRecruitParticipationRepository
                        .findByPostIdAndUserId(recruitPostId, userId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.POST_REVIEW_ACTIVITY_NOT_FOUND));
        requireReviewable(
                participation.getStatus() == MeetingRecruitParticipationStatus.REVIEWED,
                participation.getStatus() == MeetingRecruitParticipationStatus.COMPLETED);
        participation.review();
    }

    /** 이미 후기가 있으면 ALREADY_EXISTS, 완료 상태가 아니면 NOT_COMPLETED로 구분해서 안내한다. */
    private void requireReviewable(boolean alreadyReviewed, boolean completed) {
        if (alreadyReviewed) {
            throw new BusinessException(ErrorCode.POST_REVIEW_ALREADY_EXISTS);
        }
        if (!completed) {
            throw new BusinessException(ErrorCode.POST_REVIEW_ACTIVITY_NOT_COMPLETED);
        }
    }
}
