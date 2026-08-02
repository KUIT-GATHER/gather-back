package com.gather.gather.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.meeting.entity.Meeting;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostLikeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long MEETING_ID = 10L;
    private static final Long POST_ID = 100L;

    @Mock private PostLikeRepository postLikeRepository;
    @Mock private PostRepository postRepository;
    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;

    @InjectMocks private PostLikeService postLikeService;

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
    @DisplayName("좋아요가 없으면 등록하고 liked=true, 카운트를 증가시킨다")
    void toggleLike_registersWhenAbsent() {
        Post post = postInMeeting();
        when(post.getLikeCount()).thenReturn(11);
        stubMemberAndPost(true, post);
        when(postLikeRepository.findByPostIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.empty());

        PostLikeResponse response = postLikeService.toggleLike(MEETING_ID, POST_ID);

        assertThat(response.liked()).isTrue();
        verify(postLikeRepository).save(Mockito.any(PostLike.class));
        verify(post).increaseLikeCount();
    }

    @Test
    @DisplayName("이미 좋아요면 취소하고 liked=false, 카운트를 감소시킨다")
    void toggleLike_cancelsWhenPresent() {
        Post post = postInMeeting();
        when(post.getLikeCount()).thenReturn(9);
        stubMemberAndPost(true, post);
        PostLike like = Mockito.mock(PostLike.class);
        when(postLikeRepository.findByPostIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(like));

        PostLikeResponse response = postLikeService.toggleLike(MEETING_ID, POST_ID);

        assertThat(response.liked()).isFalse();
        verify(postLikeRepository).delete(like);
        verify(post).decreaseLikeCount();
        verify(postLikeRepository, never()).save(Mockito.any());
    }

    @Test
    @DisplayName("미가입자는 좋아요를 누를 수 없다")
    void toggleLike_rejectsNonMember() {
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(Mockito.mock(Meeting.class)));
        when(meetingMemberRepository.existsByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(false);

        assertThatThrownBy(() -> postLikeService.toggleLike(MEETING_ID, POST_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEETING_MEMBER_REQUIRED);
        verify(postRepository, never()).findByIdFetchUser(Mockito.anyLong());
    }

    private void stubMemberAndPost(boolean member, Post post) {
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(Mockito.mock(Meeting.class)));
        when(meetingMemberRepository.existsByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(member);
        when(postRepository.findByIdFetchUser(POST_ID)).thenReturn(Optional.of(post));
    }

    private Post postInMeeting() {
        Post post = Mockito.mock(Post.class);
        Meeting meeting = Mockito.mock(Meeting.class);
        when(post.getMeeting()).thenReturn(meeting);
        when(meeting.getId()).thenReturn(MEETING_ID);
        return post;
    }
}
