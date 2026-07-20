package com.gather.gather.domain.post.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberRole;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.post.dto.PostCreateRequest;
import com.gather.gather.domain.post.dto.PostResponse;
import com.gather.gather.domain.post.dto.PostSummaryResponse;
import com.gather.gather.domain.post.dto.PostUpdateRequest;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.enums.PostType;
import com.gather.gather.domain.post.repository.PostRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 우리모임 게시판(post) 비즈니스 로직.
 *
 * <p>권한 정책
 *
 * <ul>
 *   <li>목록/상세 열람: 가입자는 전체 유형, 미가입자는 공지·후기만
 *   <li>작성: 모임 가입자만. 공지({@code NOTICE})는 모임장(HOST)만
 *   <li>수정: 작성자 본인만
 *   <li>삭제: 작성자 본인 또는 모임장
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final UserRepository userRepository;

    public List<PostSummaryResponse> getPosts(Long meetingId, PostType typeFilter) {
        Long userId = SecurityUtil.getCurrentUserId();
        getMeeting(meetingId);

        boolean member = isApprovedMember(meetingId, userId);
        List<PostType> visibleTypes = resolveVisibleTypes(member, typeFilter);
        if (visibleTypes.isEmpty()) {
            return List.of();
        }

        return postRepository.findVisiblePosts(meetingId, visibleTypes).stream()
                .map(PostSummaryResponse::from)
                .toList();
    }

    public PostResponse getPost(Long meetingId, Long postId) {
        Long userId = SecurityUtil.getCurrentUserId();
        getMeeting(meetingId);

        Post post = getPostInMeeting(meetingId, postId);

        boolean member = isApprovedMember(meetingId, userId);
        if (!member && !post.getType().isVisibleToNonMember()) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }

        return PostResponse.from(post);
    }

    @Transactional
    public PostResponse createPost(Long meetingId, PostCreateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Meeting meeting = getMeeting(meetingId);
        MeetingMember membership = getApprovedMembership(meetingId, userId);

        if (request.type().isNotice() && membership.getRole() != MeetingMemberRole.HOST) {
            throw new BusinessException(ErrorCode.NOTICE_HOST_ONLY);
        }

        User author = getUser(userId);
        Post post =
                Post.create(
                        meeting,
                        author,
                        request.title(),
                        request.content(),
                        request.type(),
                        request.recruitCapacity());

        return PostResponse.from(postRepository.save(post));
    }

    @Transactional
    public PostResponse updatePost(Long meetingId, Long postId, PostUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        getMeeting(meetingId);

        Post post = getPostInMeeting(meetingId, postId);
        if (!post.isAuthor(userId)) {
            throw new BusinessException(ErrorCode.POST_FORBIDDEN);
        }

        post.update(request.title(), request.content());
        return PostResponse.from(post);
    }

    @Transactional
    public void deletePost(Long meetingId, Long postId) {
        Long userId = SecurityUtil.getCurrentUserId();
        getMeeting(meetingId);

        Post post = getPostInMeeting(meetingId, postId);
        if (!post.isAuthor(userId) && !isHost(meetingId, userId)) {
            throw new BusinessException(ErrorCode.POST_FORBIDDEN);
        }

        post.delete();
    }

    private List<PostType> resolveVisibleTypes(boolean member, PostType typeFilter) {
        EnumSet<PostType> allowed =
                member
                        ? EnumSet.allOf(PostType.class)
                        : EnumSet.copyOf(PostType.visibleToNonMember());
        if (typeFilter == null) {
            return List.copyOf(allowed);
        }
        return allowed.contains(typeFilter) ? List.of(typeFilter) : List.of();
    }

    private Meeting getMeeting(Long meetingId) {
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

    private User getUser(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private boolean isApprovedMember(Long meetingId, Long userId) {
        return meetingMemberRepository.existsByMeeting_IdAndUser_IdAndStatus(
                meetingId, userId, MeetingMemberStatus.APPROVED);
    }

    private MeetingMember getApprovedMembership(Long meetingId, Long userId) {
        return findApprovedMembership(meetingId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_MEMBER_REQUIRED));
    }

    private boolean isHost(Long meetingId, Long userId) {
        return findApprovedMembership(meetingId, userId)
                .map(member -> member.getRole() == MeetingMemberRole.HOST)
                .orElse(false);
    }

    private Optional<MeetingMember> findApprovedMembership(Long meetingId, Long userId) {
        return meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                meetingId, userId, MeetingMemberStatus.APPROVED);
    }
}
