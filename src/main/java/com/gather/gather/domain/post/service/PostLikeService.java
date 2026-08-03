package com.gather.gather.domain.post.service;

import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.post.dto.PostLikeResponse;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.entity.PostLike;
import com.gather.gather.domain.post.repository.PostLikeRepository;
import com.gather.gather.domain.post.repository.PostRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 좋아요 토글.
 *
 * <p>모임 가입자만 좋아요를 누를 수 있다. 이미 눌렀으면 취소(물리 삭제), 아니면 등록하며 {@code post.likeCount}를 함께 증감한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostLikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;

    @Transactional
    public PostLikeResponse toggleLike(Long meetingId, Long postId) {
        Long userId = SecurityUtil.getCurrentUserId();
        getMeeting(meetingId);
        requireApprovedMember(meetingId, userId);

        Post post = getPostInMeeting(meetingId, postId);

        Optional<PostLike> existing = postLikeRepository.findByPostIdAndUserId(postId, userId);
        if (existing.isPresent()) {
            postLikeRepository.delete(existing.get());
            post.decreaseLikeCount();
            return new PostLikeResponse(false, post.getLikeCount());
        }

        try {
            postLikeRepository.save(PostLike.create(postId, userId));
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 이미 좋아요가 저장된 경우: 현재 상태(좋아요됨)를 그대로 반환한다.
            return new PostLikeResponse(true, post.getLikeCount());
        }
        post.increaseLikeCount();
        return new PostLikeResponse(true, post.getLikeCount());
    }

    private com.gather.gather.domain.meeting.entity.Meeting getMeeting(Long meetingId) {
        return meetingRepository
                .findByIdAndDeletedAtIsNull(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
    }

    private Post getPostInMeeting(Long meetingId, Long postId) {
        Post post =
                postRepository
                        .findByIdFetchUser(postId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.getMeeting().getId().equals(meetingId)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private void requireApprovedMember(Long meetingId, Long userId) {
        boolean member =
                meetingMemberRepository.existsByMeeting_IdAndUser_IdAndStatus(
                        meetingId, userId, MeetingMemberStatus.APPROVED);
        if (!member) {
            throw new BusinessException(ErrorCode.MEETING_MEMBER_REQUIRED);
        }
    }
}
