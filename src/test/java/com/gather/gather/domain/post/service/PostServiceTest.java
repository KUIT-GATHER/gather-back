package com.gather.gather.domain.post.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.service.NotificationCreateService;
import com.gather.gather.domain.post.dto.PostCreateRequest;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.enums.PostType;
import com.gather.gather.domain.post.repository.PostRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final Long MEETING_ID = 1L;
    private static final Long POST_ID = 10L;
    private static final Long AUTHOR_ID = 20L;
    private static final Long RECIPIENT_ID = 30L;
    private static final String MEETING_NAME = "한강공원 플로깅팀";
    private static final String AUTHOR_NICKNAME = "연석";

    @Mock private PostRepository postRepository;
    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationCreateService notificationCreateService;

    private PostService postService;
    private Meeting meeting;
    private User author;
    private User recipient;

    @BeforeEach
    void setUp() {
        postService =
                new PostService(
                        postRepository,
                        meetingRepository,
                        meetingMemberRepository,
                        userRepository,
                        notificationCreateService);

        meeting = mock(Meeting.class);
        author = mock(User.class);
        recipient = mock(User.class);

        when(meeting.getId()).thenReturn(MEETING_ID);
        when(meeting.getName()).thenReturn(MEETING_NAME);
        when(author.getId()).thenReturn(AUTHOR_ID);
        when(author.getNickname()).thenReturn(AUTHOR_NICKNAME);

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(AUTHOR_ID, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("일반 게시글을 생성하면 작성자를 제외한 승인 구성원에게 알림을 생성한다")
    void createPost_createsNotificationForApprovedMembersExceptAuthor() {
        MeetingMember authorMembership = MeetingMember.createMember(author, meeting);
        authorMembership.approve();

        MeetingMember recipientMembership = MeetingMember.createMember(recipient, meeting);
        recipientMembership.approve();

        when(recipient.getId()).thenReturn(RECIPIENT_ID);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, AUTHOR_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(authorMembership));
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(author));
        when(meetingMemberRepository.findAllByMeetingIdAndStatusFetchUser(
                        MEETING_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(List.of(authorMembership, recipientMembership));
        when(postRepository.save(any(Post.class)))
                .thenAnswer(
                        invocation -> {
                            Post post = invocation.getArgument(0);
                            ReflectionTestUtils.setField(post, "id", POST_ID);
                            return post;
                        });

        PostCreateRequest request =
                new PostCreateRequest("오늘의 활동", "오늘 활동 내용을 공유합니다.", PostType.FREE, null);

        postService.createPost(MEETING_ID, request);

        verify(notificationCreateService)
                .createAll(
                        List.of(RECIPIENT_ID),
                        NotificationType.MEETING_POST_CREATED,
                        "[한강공원 플로깅팀]에 연석님이 새 게시글을 등록했어요.",
                        NotificationTargetType.POST,
                        POST_ID,
                        MEETING_ID);
    }

    @Test
    @DisplayName("공지를 생성하면 작성자를 제외한 승인 구성원에게 공지 알림을 생성한다")
    void createPost_createsNoticeNotification() {
        MeetingMember hostMembership = MeetingMember.createHost(author, meeting);

        MeetingMember recipientMembership = MeetingMember.createMember(recipient, meeting);
        recipientMembership.approve();

        when(recipient.getId()).thenReturn(RECIPIENT_ID);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, AUTHOR_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(hostMembership));
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(author));
        when(meetingMemberRepository.findAllByMeetingIdAndStatusFetchUser(
                        MEETING_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(List.of(hostMembership, recipientMembership));
        when(postRepository.save(any(Post.class)))
                .thenAnswer(
                        invocation -> {
                            Post post = invocation.getArgument(0);
                            ReflectionTestUtils.setField(post, "id", POST_ID);
                            return post;
                        });

        PostCreateRequest request =
                new PostCreateRequest("필독 공지", "모임 공지 내용입니다.", PostType.NOTICE, null);

        postService.createPost(MEETING_ID, request);

        verify(notificationCreateService)
                .createAll(
                        List.of(RECIPIENT_ID),
                        NotificationType.MEETING_NOTICE_CREATED,
                        "[한강공원 플로깅팀]에 새 공지가 등록되었어요.",
                        NotificationTargetType.POST,
                        POST_ID,
                        MEETING_ID);
    }
}
