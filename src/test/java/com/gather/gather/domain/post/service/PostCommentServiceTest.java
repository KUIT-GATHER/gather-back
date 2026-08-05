package com.gather.gather.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.event.BadgeAwardRequestedEvent;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
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
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PostCommentServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long MEETING_ID = 10L;
    private static final Long POST_ID = 100L;
    private static final Long COMMENT_ID = 1000L;

    @Mock private PostCommentRepository postCommentRepository;
    @Mock private PostRepository postRepository;
    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private PostCommentService postCommentService;

    private MockedStatic<SecurityUtil> securityUtil;

    @BeforeEach
    void setUp() {
        securityUtil = Mockito.mockStatic(SecurityUtil.class);
        securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        securityUtil.close();
    }

    @Test
    @DisplayName("가입자는 댓글을 작성하고 게시글 댓글 수가 증가한다")
    void createComment_increasesCommentCount() {
        Meeting meeting = meetingWithId();
        Post post = postInMeeting(meeting);
        MeetingMember member = approvedMember(MeetingMemberRole.MEMBER);
        User authorUser = author(USER_ID);
        PostComment saved = comment(authorUser);

        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(member));
        when(postRepository.findByIdFetchUser(POST_ID)).thenReturn(Optional.of(post));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(authorUser));
        when(postCommentRepository.save(Mockito.any(PostComment.class))).thenReturn(saved);

        PostCommentResponse response =
                postCommentService.createComment(
                        MEETING_ID, POST_ID, new PostCommentCreateRequest("좋은 글이에요"));

        assertThat(response.canEdit()).isTrue();
        assertThat(response.canDelete()).isTrue();
        verify(post).increaseCommentCount();
    }

    @Test
    @DisplayName("10번째 댓글을 작성하면 COMMENT_10 뱃지 이벤트를 발행한다")
    void createComment_publishesBadgeEventAtTenthComment() {
        Meeting meeting = meetingWithId();
        Post post = postInMeeting(meeting);
        MeetingMember member = approvedMember(MeetingMemberRole.MEMBER);
        User authorUser = author(USER_ID);
        PostComment saved = comment(authorUser);

        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(member));
        when(postRepository.findByIdFetchUser(POST_ID)).thenReturn(Optional.of(post));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(authorUser));
        when(postCommentRepository.save(Mockito.any(PostComment.class))).thenReturn(saved);
        when(postCommentRepository.countByUser_IdAndDeletedAtIsNull(USER_ID)).thenReturn(10L);

        postCommentService.createComment(
                MEETING_ID, POST_ID, new PostCommentCreateRequest("좋은 글이에요"));

        verify(eventPublisher)
                .publishEvent(new BadgeAwardRequestedEvent(USER_ID, BadgeType.COMMENT_10));
    }

    @Test
    @DisplayName("아직 10개 미만이면 COMMENT_10 뱃지 이벤트를 발행하지 않는다")
    void createComment_doesNotPublishBadgeEventBeforeTenthComment() {
        Meeting meeting = meetingWithId();
        Post post = postInMeeting(meeting);
        MeetingMember member = approvedMember(MeetingMemberRole.MEMBER);
        User authorUser = author(USER_ID);
        PostComment saved = comment(authorUser);

        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(member));
        when(postRepository.findByIdFetchUser(POST_ID)).thenReturn(Optional.of(post));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(authorUser));
        when(postCommentRepository.save(Mockito.any(PostComment.class))).thenReturn(saved);
        when(postCommentRepository.countByUser_IdAndDeletedAtIsNull(USER_ID)).thenReturn(9L);

        postCommentService.createComment(
                MEETING_ID, POST_ID, new PostCommentCreateRequest("좋은 글이에요"));

        verify(eventPublisher, never()).publishEvent(Mockito.any(BadgeAwardRequestedEvent.class));
    }

    @Test
    @DisplayName("미가입자가 댓글을 작성하면 MEETING_MEMBER_REQUIRED로 거부한다")
    void createComment_rejectsNonMember() {
        Meeting meeting = meetingWithId();
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                postCommentService.createComment(
                                        MEETING_ID, POST_ID, new PostCommentCreateRequest("내용")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEETING_MEMBER_REQUIRED);
        verify(postCommentRepository, never()).save(Mockito.any());
    }

    @Test
    @DisplayName("작성자가 아니면 댓글을 수정할 수 없다")
    void updateComment_rejectsNonAuthor() {
        Meeting meeting = meetingWithId();
        Post post = postInMeeting(meeting);
        PostComment comment = comment(author(OTHER_ID));

        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(postRepository.findByIdFetchUser(POST_ID)).thenReturn(Optional.of(post));
        when(postCommentRepository.findByIdFetchUser(COMMENT_ID)).thenReturn(Optional.of(comment));

        assertThatThrownBy(
                        () ->
                                postCommentService.updateComment(
                                        MEETING_ID,
                                        POST_ID,
                                        COMMENT_ID,
                                        new PostCommentUpdateRequest("수정")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_FORBIDDEN);
    }

    @Test
    @DisplayName("팀장은 타인 댓글을 삭제할 수 있고 댓글 수가 감소한다")
    void deleteComment_allowsHostAndDecreasesCount() {
        Meeting meeting = meetingWithId();
        Post post = postInMeeting(meeting);
        PostComment comment = comment(author(OTHER_ID));
        MeetingMember host = approvedMember(MeetingMemberRole.HOST);

        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(postRepository.findByIdFetchUser(POST_ID)).thenReturn(Optional.of(post));
        when(postCommentRepository.findByIdFetchUser(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(host));

        postCommentService.deleteComment(MEETING_ID, POST_ID, COMMENT_ID);

        verify(comment).delete();
        verify(post).decreaseCommentCount();
    }

    // ---------- fixtures ----------

    private Meeting meetingWithId() {
        Meeting meeting = Mockito.mock(Meeting.class);
        Mockito.lenient().when(meeting.getId()).thenReturn(MEETING_ID);
        return meeting;
    }

    private Post postInMeeting(Meeting meeting) {
        Post post = Mockito.mock(Post.class);
        Mockito.lenient().when(post.getMeeting()).thenReturn(meeting);
        return post;
    }

    private MeetingMember approvedMember(MeetingMemberRole role) {
        MeetingMember member = Mockito.mock(MeetingMember.class);
        Mockito.lenient().when(member.getRole()).thenReturn(role);
        return member;
    }

    private User author(Long id) {
        User user = Mockito.mock(User.class);
        Mockito.lenient().when(user.getId()).thenReturn(id);
        Mockito.lenient().when(user.getNickname()).thenReturn("닉네임");
        return user;
    }

    private PostComment comment(User author) {
        PostComment comment = Mockito.mock(PostComment.class);
        Post ownerPost = Mockito.mock(Post.class);
        Mockito.lenient().when(ownerPost.getId()).thenReturn(POST_ID);
        Mockito.lenient().when(comment.getId()).thenReturn(COMMENT_ID);
        Mockito.lenient().when(comment.getUser()).thenReturn(author);
        Mockito.lenient().when(comment.getContent()).thenReturn("내용");
        Mockito.lenient().when(comment.getCreatedAt()).thenReturn(LocalDateTime.now());
        Mockito.lenient().when(comment.getUpdatedAt()).thenReturn(LocalDateTime.now());
        Mockito.lenient().when(comment.getPost()).thenReturn(ownerPost);
        Mockito.lenient()
                .when(comment.isAuthor(Mockito.anyLong()))
                .thenAnswer(inv -> author.getId().equals(inv.getArgument(0)));
        return comment;
    }
}
