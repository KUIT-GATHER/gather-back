package com.gather.gather.domain.recruit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberRole;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.notification.event.MeetingPostNotificationRequestedEvent;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.enums.PostType;
import com.gather.gather.domain.post.repository.PostRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.recruit.dto.RecruitCreateRequest;
import com.gather.gather.domain.recruit.dto.RecruitDetailResponse;
import com.gather.gather.domain.recruit.dto.RecruitParticipationResponse;
import com.gather.gather.domain.recruit.dto.RecruitUpdateRequest;
import com.gather.gather.domain.recruit.entity.MeetingRecruit;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipation;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import com.gather.gather.domain.recruit.repository.MeetingRecruitParticipationRepository;
import com.gather.gather.domain.recruit.repository.MeetingRecruitRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
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
class MeetingRecruitServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long MEETING_ID = 10L;
    private static final Long POST_ID = 100L;
    private static final Long PARTICIPATION_ID = 500L;

    @Mock private PostRepository postRepository;
    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private MeetingRecruitRepository meetingRecruitRepository;
    @Mock private MeetingRecruitParticipationRepository participationRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private MeetingRecruitService meetingRecruitService;

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
    @DisplayName("팀장은 모집공고를 작성하고 Post와 확장 정보가 함께 저장된다")
    void createRecruit_savesPostAndRecruit() {
        Meeting meeting = meeting();
        MeetingMember host = member(MeetingMemberRole.HOST);
        User author = author();
        Post savedPost = recruitPost();

        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(host));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(author));
        when(postRepository.save(any(Post.class))).thenReturn(savedPost);
        when(meetingRecruitRepository.save(any(MeetingRecruit.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RecruitDetailResponse response =
                meetingRecruitService.createRecruit(MEETING_ID, createRequest(30, false, null));

        assertThat(response.postId()).isEqualTo(POST_ID);
        assertThat(response.maxParticipants()).isEqualTo(30);
        assertThat(response.appliedCount()).isZero();
        assertThat(response.applied()).isFalse();
        assertThat(response.canEdit()).isTrue();
        verify(meetingRecruitRepository).save(any(MeetingRecruit.class));
        verify(eventPublisher).publishEvent(any(MeetingPostNotificationRequestedEvent.class));
    }

    @Test
    @DisplayName("팀원이 모집공고를 작성하면 RECRUIT_HOST_ONLY로 거부한다")
    void createRecruit_rejectsNonHost() {
        Meeting meeting = meeting();
        MeetingMember member = member(MeetingMemberRole.MEMBER);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(
                        () ->
                                meetingRecruitService.createRecruit(
                                        MEETING_ID, createRequest(30, false, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECRUIT_HOST_ONLY);
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("봉사시간 인정인데 시간이 없으면 거부한다")
    void createRecruit_rejectsRecognizedWithoutMinutes() {
        Meeting meeting = meeting();
        MeetingMember host = member(MeetingMemberRole.HOST);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(host));

        assertThatThrownBy(
                        () ->
                                meetingRecruitService.createRecruit(
                                        MEETING_ID, createRequest(30, true, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", ErrorCode.RECRUIT_RECOGNIZED_MINUTES_REQUIRED);
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("정원·기간이 열려 있고 미신청이면 신청되고 현황이 1 증가한다")
    void toggleParticipation_appliesWhenOpen() {
        stubMemberAndRecruitPost(true, openRecruit(4));
        when(participationRepository.countByPostId(POST_ID)).thenReturn(2L);
        when(participationRepository.findByPostIdAndUserId(POST_ID, USER_ID))
                .thenReturn(Optional.empty());

        RecruitParticipationResponse response =
                meetingRecruitService.toggleParticipation(MEETING_ID, POST_ID);

        assertThat(response.applied()).isTrue();
        assertThat(response.appliedCount()).isEqualTo(3);
        assertThat(response.maxParticipants()).isEqualTo(4);
        verify(participationRepository).save(any(MeetingRecruitParticipation.class));
    }

    @Test
    @DisplayName("이미 신청했으면 취소되고 현황이 1 감소한다")
    void toggleParticipation_cancelsWhenApplied() {
        stubMemberAndRecruitPost(true, openRecruit(4));
        when(participationRepository.countByPostId(POST_ID)).thenReturn(3L);
        MeetingRecruitParticipation participation = Mockito.mock(MeetingRecruitParticipation.class);
        when(participationRepository.findByPostIdAndUserId(POST_ID, USER_ID))
                .thenReturn(Optional.of(participation));

        RecruitParticipationResponse response =
                meetingRecruitService.toggleParticipation(MEETING_ID, POST_ID);

        assertThat(response.applied()).isFalse();
        assertThat(response.appliedCount()).isEqualTo(2);
        verify(participationRepository).delete(participation);
        verify(participationRepository, never()).save(any());
    }

    @Test
    @DisplayName("신청 마감일이 지나면 신청·취소 모두 거부한다")
    void toggleParticipation_rejectsAfterDeadline() {
        stubMemberAndRecruitPost(true, closedRecruit(4));

        assertThatThrownBy(() -> meetingRecruitService.toggleParticipation(MEETING_ID, POST_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECRUIT_APPLICATION_CLOSED);
        verify(participationRepository, never()).save(any());
    }

    @Test
    @DisplayName("정원이 찼으면 신청을 거부한다")
    void toggleParticipation_rejectsWhenFull() {
        stubMemberAndRecruitPost(true, openRecruit(4));
        when(participationRepository.countByPostId(POST_ID)).thenReturn(4L);
        when(participationRepository.findByPostIdAndUserId(POST_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingRecruitService.toggleParticipation(MEETING_ID, POST_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECRUIT_CAPACITY_EXCEEDED);
        verify(participationRepository, never()).save(any());
    }

    @Test
    @DisplayName("미가입자는 모집공고 상세를 볼 수 없다")
    void getRecruit_rejectsNonMember() {
        Meeting meeting = meeting();
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.existsByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(false);

        assertThatThrownBy(() -> meetingRecruitService.getRecruit(MEETING_ID, POST_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_ACCESS_DENIED);
    }

    @Test
    @DisplayName("작성자(팀장)는 모집공고를 수정할 수 있다")
    void updateRecruit_updatesWhenAuthor() {
        Meeting meeting = meeting();
        Post post = recruitPost();
        when(post.isAuthor(USER_ID)).thenReturn(true);
        MeetingRecruit recruit = openRecruit(30);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(postRepository.findByIdFetchUser(POST_ID)).thenReturn(Optional.of(post));
        when(meetingRecruitRepository.findByPostId(POST_ID)).thenReturn(Optional.of(recruit));
        when(participationRepository.countByPostId(POST_ID)).thenReturn(2L);
        when(participationRepository.existsByPostIdAndUserId(POST_ID, USER_ID)).thenReturn(false);

        RecruitDetailResponse response =
                meetingRecruitService.updateRecruit(MEETING_ID, POST_ID, updateRequest(40));

        assertThat(response.maxParticipants()).isEqualTo(40);
        assertThat(response.appliedCount()).isEqualTo(2);
        verify(post).update("6월 정기 활동 팀원 모집(수정)", "소개 수정");
    }

    @Test
    @DisplayName("작성자가 아니면 모집공고를 수정할 수 없다")
    void updateRecruit_rejectsNonAuthor() {
        Meeting meeting = meeting();
        Post post = recruitPost();
        when(post.isAuthor(USER_ID)).thenReturn(false);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(postRepository.findByIdFetchUser(POST_ID)).thenReturn(Optional.of(post));

        assertThatThrownBy(
                        () ->
                                meetingRecruitService.updateRecruit(
                                        MEETING_ID, POST_ID, updateRequest(40)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_FORBIDDEN);
    }

    @Test
    @DisplayName("현재 신청 인원보다 정원을 적게 줄이면 거부한다")
    void updateRecruit_rejectsMaxBelowApplied() {
        Meeting meeting = meeting();
        Post post = recruitPost();
        when(post.isAuthor(USER_ID)).thenReturn(true);
        MeetingRecruit recruit = openRecruit(30);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(postRepository.findByIdFetchUser(POST_ID)).thenReturn(Optional.of(post));
        when(meetingRecruitRepository.findByPostId(POST_ID)).thenReturn(Optional.of(recruit));
        when(participationRepository.countByPostId(POST_ID)).thenReturn(5L);

        assertThatThrownBy(
                        () ->
                                meetingRecruitService.updateRecruit(
                                        MEETING_ID, POST_ID, updateRequest(3)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECRUIT_MAX_BELOW_APPLIED);
        verify(post, never()).update(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    @DisplayName("팀장은 신청(APPLIED) 참여를 확정한다")
    void confirmParticipation_confirmsAppliedParticipation_whenHost() {
        Meeting meeting = meeting();
        Post post = recruitPost();
        MeetingMember host = member(MeetingMemberRole.HOST);
        MeetingRecruitParticipation participation = participation(MeetingRecruitParticipationStatus.APPLIED);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(host));
        when(postRepository.findByIdFetchUser(POST_ID)).thenReturn(Optional.of(post));
        when(participationRepository.findById(PARTICIPATION_ID))
                .thenReturn(Optional.of(participation));

        meetingRecruitService.confirmParticipation(MEETING_ID, POST_ID, PARTICIPATION_ID);

        verify(participation).confirm();
    }

    @Test
    @DisplayName("팀원이 참여를 확정하려 하면 RECRUIT_HOST_ONLY로 거부한다")
    void confirmParticipation_rejectsNonHost() {
        Meeting meeting = meeting();
        MeetingMember member = member(MeetingMemberRole.MEMBER);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(
                        () ->
                                meetingRecruitService.confirmParticipation(
                                        MEETING_ID, POST_ID, PARTICIPATION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECRUIT_HOST_ONLY);
    }

    @Test
    @DisplayName("이미 확정된 참여는 다시 확정할 수 없다")
    void confirmParticipation_rejectsWhenNotApplied() {
        Meeting meeting = meeting();
        Post post = recruitPost();
        MeetingMember host = member(MeetingMemberRole.HOST);
        MeetingRecruitParticipation participation =
                participation(MeetingRecruitParticipationStatus.CONFIRMED);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(host));
        when(postRepository.findByIdFetchUser(POST_ID)).thenReturn(Optional.of(post));
        when(participationRepository.findById(PARTICIPATION_ID))
                .thenReturn(Optional.of(participation));

        assertThatThrownBy(
                        () ->
                                meetingRecruitService.confirmParticipation(
                                        MEETING_ID, POST_ID, PARTICIPATION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", ErrorCode.RECRUIT_PARTICIPATION_CONFIRM_NOT_ALLOWED);
        verify(participation, never()).confirm();
    }

    @Test
    @DisplayName("활동 종료 후 팀장은 확정된 참가자를 참석 처리해 봉사완료로 전환한다")
    void markParticipantPresent_completesConfirmedParticipation_afterActivityEnded() {
        Meeting meeting = meeting();
        Post post = recruitPost();
        MeetingMember host = member(MeetingMemberRole.HOST);
        MeetingRecruit recruit = closedRecruit(4);
        MeetingRecruitParticipation participation =
                participation(MeetingRecruitParticipationStatus.CONFIRMED);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(host));
        when(postRepository.findByIdFetchUser(POST_ID)).thenReturn(Optional.of(post));
        when(meetingRecruitRepository.findByPostId(POST_ID)).thenReturn(Optional.of(recruit));
        when(participationRepository.findById(PARTICIPATION_ID))
                .thenReturn(Optional.of(participation));

        meetingRecruitService.markParticipantPresent(MEETING_ID, POST_ID, PARTICIPATION_ID);

        verify(participation).complete();
    }

    @Test
    @DisplayName("활동이 끝나기 전에는 참석 처리를 할 수 없다")
    void markParticipantPresent_rejectsBeforeActivityEnds() {
        Meeting meeting = meeting();
        Post post = recruitPost();
        MeetingMember host = member(MeetingMemberRole.HOST);
        MeetingRecruit recruit = openRecruit(4);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(host));
        when(postRepository.findByIdFetchUser(POST_ID)).thenReturn(Optional.of(post));
        when(meetingRecruitRepository.findByPostId(POST_ID)).thenReturn(Optional.of(recruit));

        assertThatThrownBy(
                        () ->
                                meetingRecruitService.markParticipantPresent(
                                        MEETING_ID, POST_ID, PARTICIPATION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECRUIT_ACTIVITY_NOT_ENDED);
        verify(participationRepository, never()).findById(PARTICIPATION_ID);
    }

    @Test
    @DisplayName("확정되지 않은 참여는 참석 처리를 할 수 없다")
    void markParticipantPresent_rejectsWhenNotConfirmed() {
        Meeting meeting = meeting();
        Post post = recruitPost();
        MeetingMember host = member(MeetingMemberRole.HOST);
        MeetingRecruit recruit = closedRecruit(4);
        MeetingRecruitParticipation participation =
                participation(MeetingRecruitParticipationStatus.APPLIED);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(host));
        when(postRepository.findByIdFetchUser(POST_ID)).thenReturn(Optional.of(post));
        when(meetingRecruitRepository.findByPostId(POST_ID)).thenReturn(Optional.of(recruit));
        when(participationRepository.findById(PARTICIPATION_ID))
                .thenReturn(Optional.of(participation));

        assertThatThrownBy(
                        () ->
                                meetingRecruitService.markParticipantPresent(
                                        MEETING_ID, POST_ID, PARTICIPATION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECRUIT_PRESENT_NOT_ALLOWED);
        verify(participation, never()).complete();
    }

    // ---------- fixtures ----------
    // 주의: 스텁을 만드는 헬퍼(meeting()/recruitPost()/member()/author())는 반드시 지역변수로 먼저 받은 뒤
    // when(...) 안에서 쓴다. when(...).thenReturn(헬퍼())처럼 중첩하면 UnfinishedStubbingException이 난다.

    private void stubMemberAndRecruitPost(boolean member, MeetingRecruit recruit) {
        Meeting meeting = meeting();
        Post post = recruitPost();
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.existsByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(member);
        when(postRepository.findByIdFetchUser(POST_ID)).thenReturn(Optional.of(post));
        when(meetingRecruitRepository.findByPostId(POST_ID)).thenReturn(Optional.of(recruit));
    }

    private Meeting meeting() {
        Meeting meeting = Mockito.mock(Meeting.class);
        Mockito.lenient().when(meeting.getId()).thenReturn(MEETING_ID);
        return meeting;
    }

    private Post recruitPost() {
        Post post = Mockito.mock(Post.class);
        Meeting postMeeting = Mockito.mock(Meeting.class);
        Mockito.lenient().when(postMeeting.getId()).thenReturn(MEETING_ID);
        User postAuthor = Mockito.mock(User.class);
        Mockito.lenient().when(postAuthor.getId()).thenReturn(USER_ID);
        Mockito.lenient().when(postAuthor.getNickname()).thenReturn("이수진");
        Mockito.lenient().when(post.getId()).thenReturn(POST_ID);
        Mockito.lenient().when(post.getMeeting()).thenReturn(postMeeting);
        Mockito.lenient().when(post.getType()).thenReturn(PostType.RECRUIT);
        Mockito.lenient().when(post.getTitle()).thenReturn("6월 정기 활동 팀원 모집");
        Mockito.lenient().when(post.getContent()).thenReturn("소개");
        Mockito.lenient().when(post.getUser()).thenReturn(postAuthor);
        return post;
    }

    private MeetingMember member(MeetingMemberRole role) {
        MeetingMember member = Mockito.mock(MeetingMember.class);
        Mockito.lenient().when(member.getRole()).thenReturn(role);
        return member;
    }

    private MeetingRecruitParticipation participation(MeetingRecruitParticipationStatus status) {
        MeetingRecruitParticipation participation = Mockito.mock(MeetingRecruitParticipation.class);
        Mockito.lenient().when(participation.getId()).thenReturn(PARTICIPATION_ID);
        Mockito.lenient().when(participation.getPostId()).thenReturn(POST_ID);
        Mockito.lenient().when(participation.getStatus()).thenReturn(status);
        return participation;
    }

    private User author() {
        User user = Mockito.mock(User.class);
        Mockito.lenient().when(user.getId()).thenReturn(USER_ID);
        Mockito.lenient().when(user.getNickname()).thenReturn("이수진");
        return user;
    }

    private RecruitCreateRequest createRequest(
            int maxParticipants, boolean timeRecognized, Integer recognizedMinutes) {
        return new RecruitCreateRequest(
                "6월 정기 활동 팀원 모집",
                "소개",
                "서울 영등포구 여의도동",
                LocalDate.now().plusDays(5),
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                maxParticipants,
                Set.of(PostingCategory.ENVIRONMENT),
                timeRecognized,
                recognizedMinutes,
                LocalDate.now().plusDays(10),
                false);
    }

    private RecruitUpdateRequest updateRequest(int maxParticipants) {
        return new RecruitUpdateRequest(
                "6월 정기 활동 팀원 모집(수정)",
                "소개 수정",
                "서울 영등포구 여의도동",
                LocalDate.now().plusDays(5),
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                maxParticipants,
                Set.of(PostingCategory.ENVIRONMENT),
                false,
                null,
                LocalDate.now().plusDays(10),
                false);
    }

    private MeetingRecruit openRecruit(int maxParticipants) {
        return MeetingRecruit.create(
                POST_ID,
                "서울 영등포구 여의도동",
                LocalDate.now().plusDays(5),
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                maxParticipants,
                false,
                null,
                LocalDate.now().plusDays(10),
                false,
                Set.of(PostingCategory.ENVIRONMENT));
    }

    private MeetingRecruit closedRecruit(int maxParticipants) {
        return MeetingRecruit.create(
                POST_ID,
                "서울 영등포구 여의도동",
                LocalDate.now().minusDays(5),
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                maxParticipants,
                false,
                null,
                LocalDate.now().minusDays(1),
                false,
                Set.of(PostingCategory.ENVIRONMENT));
    }
}
