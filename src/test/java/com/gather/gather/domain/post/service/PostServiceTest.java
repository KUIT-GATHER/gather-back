package com.gather.gather.domain.post.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
import com.gather.gather.domain.post.dto.PostCreateRequest;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.enums.PostType;
import com.gather.gather.domain.post.repository.PostRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** H-3: PostService에 테스트 클래스가 전무했다 — createPost의 FIRST_REVIEW 뱃지 트리거 경로를 최소한으로 커버한다. */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long MEETING_ID = 100L;

    @Mock private PostRepository postRepository;
    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PostService postService;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        postService =
                new PostService(
                        postRepository,
                        meetingRepository,
                        meetingMemberRepository,
                        userRepository,
                        eventPublisher);
        meeting = mock(Meeting.class);
    }

    @Test
    @DisplayName("createPost publishes FIRST_REVIEW when the post type is REVIEW (H-3)")
    void createPost_awardsFirstReview_whenTypeIsReview() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                    .thenReturn(Optional.of(meeting));
            MeetingMember membership = approvedMember(MeetingMemberRole.MEMBER);
            when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                            MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                    .thenReturn(Optional.of(membership));
            User author = mock(User.class);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(author));
            when(postRepository.save(any(Post.class))).thenAnswer(returnsFirstArg());

            postService.createPost(
                    MEETING_ID, new PostCreateRequest("후기 제목", "내용", PostType.REVIEW, null));

            verify(eventPublisher)
                    .publishEvent(new BadgeAwardRequestedEvent(USER_ID, BadgeType.FIRST_REVIEW));
            verify(postRepository).save(any(Post.class));
        }
    }

    @Test
    @DisplayName("createPost does not publish FIRST_REVIEW for a non-review post type")
    void createPost_doesNotAwardFirstReview_whenTypeIsNotReview() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                    .thenReturn(Optional.of(meeting));
            MeetingMember membership = approvedMember(MeetingMemberRole.MEMBER);
            when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                            MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                    .thenReturn(Optional.of(membership));
            User author = mock(User.class);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(author));
            when(postRepository.save(any(Post.class))).thenAnswer(returnsFirstArg());

            postService.createPost(
                    MEETING_ID, new PostCreateRequest("자유 게시글", "내용", PostType.FREE, null));

            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Test
    @DisplayName("createPost throws NOTICE_HOST_ONLY when a non-host writes a NOTICE post")
    void createPost_throwsNoticeHostOnly_whenNonHostWritesNotice() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                    .thenReturn(Optional.of(meeting));
            MeetingMember membership = approvedMember(MeetingMemberRole.MEMBER);
            when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                            MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                    .thenReturn(Optional.of(membership));

            assertThatThrownBy(
                            () ->
                                    postService.createPost(
                                            MEETING_ID,
                                            new PostCreateRequest(
                                                    "공지", "내용", PostType.NOTICE, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTICE_HOST_ONLY);
            verify(eventPublisher, never()).publishEvent(any());
            verify(postRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName(
            "createPost throws MEETING_MEMBER_REQUIRED when the caller is not an approved member")
    void createPost_throwsMemberRequired_whenNotApprovedMember() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                    .thenReturn(Optional.of(meeting));
            when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                            MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    postService.createPost(
                                            MEETING_ID,
                                            new PostCreateRequest("제목", "내용", PostType.FREE, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEETING_MEMBER_REQUIRED);
        }
    }

    @Test
    @DisplayName("createPost throws MEETING_NOT_FOUND when the meeting does not exist")
    void createPost_throwsMeetingNotFound_whenMeetingMissing() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    postService.createPost(
                                            MEETING_ID,
                                            new PostCreateRequest("제목", "내용", PostType.FREE, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEETING_NOT_FOUND);
        }
    }

    private MeetingMember approvedMember(MeetingMemberRole role) {
        MeetingMember member = mock(MeetingMember.class);
        org.mockito.Mockito.lenient().when(member.getRole()).thenReturn(role);
        return member;
    }
}
