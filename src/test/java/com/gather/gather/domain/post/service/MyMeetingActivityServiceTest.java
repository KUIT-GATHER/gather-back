package com.gather.gather.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.post.dto.MyMeetingActivitySummaryResponse;
import com.gather.gather.domain.post.dto.PostSummaryResponse;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.repository.PostCommentRepository;
import com.gather.gather.domain.post.repository.PostRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.util.List;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MyMeetingActivityServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long MEETING_ID = 10L;

    @Mock private PostRepository postRepository;
    @Mock private PostCommentRepository postCommentRepository;
    @Mock private PostSummaryAssembler summaryAssembler;
    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;

    @InjectMocks private MyMeetingActivityService myMeetingActivityService;

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
    @DisplayName("나의 활동 요약은 작성글·댓글단글 개수를 반환한다")
    void getActivitySummary_returnsCounts() {
        stubMember(true);
        when(postRepository.countByMeeting_IdAndUser_IdAndDeletedAtIsNull(MEETING_ID, USER_ID))
                .thenReturn(3L);
        when(postCommentRepository.countCommentedPosts(MEETING_ID, USER_ID)).thenReturn(2L);

        MyMeetingActivitySummaryResponse response =
                myMeetingActivityService.getActivitySummary(MEETING_ID);

        assertThat(response.writtenPostCount()).isEqualTo(3L);
        assertThat(response.commentedPostCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("작성한 게시글 목록은 조립기를 통해 페이지 응답으로 반환한다")
    void getMyPosts_delegatesToAssembler() {
        stubMember(true);
        Page<Post> page = new PageImpl<>(List.of());
        when(postRepository.findMyPosts(eq(MEETING_ID), eq(USER_ID), any())).thenReturn(page);
        PageResponse<PostSummaryResponse> expected = new PageResponse<>(List.of(), 0, 0, 0, 20);
        when(summaryAssembler.assemble(page, USER_ID)).thenReturn(expected);

        PageResponse<PostSummaryResponse> result =
                myMeetingActivityService.getMyPosts(MEETING_ID, PageRequest.of(0, 20));

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("미가입자는 나의 활동을 조회할 수 없다")
    void getActivitySummary_rejectsNonMember() {
        stubMember(false);

        assertThatThrownBy(() -> myMeetingActivityService.getActivitySummary(MEETING_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEETING_MEMBER_REQUIRED);
    }

    private void stubMember(boolean member) {
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(Mockito.mock(Meeting.class)));
        when(meetingMemberRepository.existsByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(member);
    }
}
