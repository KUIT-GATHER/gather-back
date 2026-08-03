package com.gather.gather.domain.post.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.enums.MeetingMemberRole;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.post.dto.PostCommentCreateRequest;
import com.gather.gather.domain.post.dto.PostCommentResponse;
import com.gather.gather.domain.post.dto.PostCommentUpdateRequest;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.entity.PostComment;
import com.gather.gather.domain.post.repository.PostCommentRepository;
import com.gather.gather.domain.post.repository.PostRepository;
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
 * 모임 게시글 댓글 비즈니스 로직.
 *
 * <p>권한 정책
 *
 * <ul>
 *   <li>목록 열람: 게시글 열람 권한과 동일(미가입자는 공지·후기 게시글의 댓글만)
 *   <li>작성: 모임 가입자만
 *   <li>수정: 작성자 본인만
 *   <li>삭제: 작성자 본인 또는 모임장(HOST)
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCommentService {

    private final PostCommentRepository postCommentRepository;
    private final PostRepository postRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final UserRepository userRepository;

    public PageResponse<PostCommentResponse> getComments(
            Long meetingId, Long postId, Pageable pageable) {
        Long userId = SecurityUtil.getCurrentUserId();
        getMeeting(meetingId);

        Post post = getPostInMeeting(meetingId, postId);
        boolean member = isApprovedMember(meetingId, userId);
        if (!member && !post.getType().isVisibleToNonMember()) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }

        boolean host = member && isHost(meetingId, userId);
        Page<PostCommentResponse> page =
                postCommentRepository
                        .findVisibleByPostId(postId, pageable)
                        .map(
                                comment ->
                                        PostCommentResponse.from(
                                                comment,
                                                comment.isAuthor(userId),
                                                comment.isAuthor(userId) || host));
        return PageResponse.from(page);
    }

    @Transactional
    public PostCommentResponse createComment(
            Long meetingId, Long postId, PostCommentCreateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        getMeeting(meetingId);
        getApprovedMembership(meetingId, userId);

        Post post = getPostInMeeting(meetingId, postId);
        User author = getUser(userId);

        PostComment comment =
                postCommentRepository.save(PostComment.create(post, author, request.content()));
        post.increaseCommentCount();

        // 작성 직후에는 본인 댓글이므로 수정·삭제 모두 가능하다.
        return PostCommentResponse.from(comment, true, true);
    }

    @Transactional
    public PostCommentResponse updateComment(
            Long meetingId, Long postId, Long commentId, PostCommentUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        getMeeting(meetingId);
        getPostInMeeting(meetingId, postId);

        PostComment comment = getCommentInPost(postId, commentId);
        if (!comment.isAuthor(userId)) {
            throw new BusinessException(ErrorCode.COMMENT_FORBIDDEN);
        }

        comment.update(request.content());
        // 수정은 작성자 본인만 도달하므로, 본인 댓글 기준 수정·삭제 모두 가능하다.
        return PostCommentResponse.from(comment, true, true);
    }

    @Transactional
    public void deleteComment(Long meetingId, Long postId, Long commentId) {
        Long userId = SecurityUtil.getCurrentUserId();
        getMeeting(meetingId);
        Post post = getPostInMeeting(meetingId, postId);

        PostComment comment = getCommentInPost(postId, commentId);
        if (!comment.isAuthor(userId) && !isHost(meetingId, userId)) {
            throw new BusinessException(ErrorCode.COMMENT_FORBIDDEN);
        }

        comment.delete();
        post.decreaseCommentCount();
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

    private PostComment getCommentInPost(Long postId, Long commentId) {
        PostComment comment =
                postCommentRepository
                        .findByIdFetchUser(commentId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getPost().getId().equals(postId)) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return comment;
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

    private void getApprovedMembership(Long meetingId, Long userId) {
        meetingMemberRepository
                .findByMeeting_IdAndUser_IdAndStatus(
                        meetingId, userId, MeetingMemberStatus.APPROVED)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_MEMBER_REQUIRED));
    }

    private boolean isHost(Long meetingId, Long userId) {
        return meetingMemberRepository
                .findByMeeting_IdAndUser_IdAndStatus(
                        meetingId, userId, MeetingMemberStatus.APPROVED)
                .map(member -> member.getRole() == MeetingMemberRole.HOST)
                .orElse(false);
    }
}
