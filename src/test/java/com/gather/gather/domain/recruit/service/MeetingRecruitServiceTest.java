package com.gather.gather.domain.recruit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.event.MeetingPostNotificationRequestedEvent;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.enums.PostType;
import com.gather.gather.domain.post.repository.PostRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.recruit.dto.RecruitCreateRequest;
import com.gather.gather.domain.recruit.dto.RecruitDetailResponse;
import com.gather.gather.domain.recruit.dto.RecruitParticipationAction;
import com.gather.gather.domain.recruit.dto.RecruitParticipationResponse;
import com.gather.gather.domain.recruit.dto.RecruitUpdateRequest;
import com.gather.gather.domain.recruit.entity.MeetingRecruit;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipation;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import com.gather.gather.domain.recruit.repository.MeetingRecruitParticipationRepository;
import com.gather.gather.domain.recruit.repository.MeetingRecruitRepository;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.DuplicateSubmissionGuard;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDateTime;
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
    private static final Long REGION_ID = 5L;

    @Mock private PostRepository postRepository;
    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private MeetingRecruitRepository meetingRecruitRepository;
    @Mock private MeetingRecruitParticipationRepository participationRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private DuplicateSubmissionGuard duplicateSubmissionGuard;

    @InjectMocks private MeetingRecruitService meetingRecruitService;

    private MockedStatic<SecurityUtil> securityUtil;

    @BeforeEach
    void setUp() {
        securityUtil = Mockito.mockStatic(SecurityUtil.class);
        securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
        securityUtil.when(SecurityUtil::getCurrentUserIdOrNull).thenReturn(USER_ID);
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
        assertThat(response.participationStatus()).isNull();
        assertThat(response.participationAction()).isEqualTo(RecruitParticipationAction.APPLY);
        assertThat(response.canEdit()).isTrue();
        assertThat(response.participationCondition()).isEqualTo("성인 및 청소년 단체 신청 가능");

        verify(meetingRecruitRepository).save(any(MeetingRecruit.class));
        verify(eventPublisher)
                .publishEvent(
                        new MeetingPostNotificationRequestedEvent(
                                MEETING_ID,
                                POST_ID,
                                USER_ID,
                                NotificationType.MEETING_POSTING_CREATED,
                                "[한강공원 플로깅]에 새 봉사공고가 등록되었어요."));
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
        verify(eventPublisher, never()).publishEvent(Mockito.any(Object.class));
        // H2: 권한 검증 실패는 가드보다 먼저 걸려야 한다.
        verify(duplicateSubmissionGuard, never()).guard(any());
    }

    @Test
    @DisplayName("공고 기반 모임의 팀장은 자체 모집공고를 작성할 수 없다")
    void createRecruit_rejectsPostingBasedMeeting() {
        Meeting meeting = meeting();
        Mockito.lenient().when(meeting.isPostingBased()).thenReturn(true);
        MeetingMember host = member(MeetingMemberRole.HOST);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(host));

        assertThatThrownBy(
                        () ->
                                meetingRecruitService.createRecruit(
                                        MEETING_ID, createRequest(30, false, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", ErrorCode.RECRUIT_POSTING_BASED_NOT_ALLOWED);

        verify(postRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(Mockito.any(Object.class));
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
        verify(eventPublisher, never()).publishEvent(Mockito.any(Object.class));
    }

    @Test
    @DisplayName("createRecruit calls DuplicateSubmissionGuard with the user:meeting key after"
            + " validation passes (H3)")
    void createRecruit_callsDuplicateSubmissionGuard_withExpectedKey_afterValidationPasses() {
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

        meetingRecruitService.createRecruit(MEETING_ID, createRequest(30, false, null));

        verify(duplicateSubmissionGuard).guard("recruit:create:" + USER_ID + ":" + MEETING_ID);
    }

    @Test
    @DisplayName("createRecruit does not save or publish events when DuplicateSubmissionGuard"
            + " rejects the request (H3)")
    void createRecruit_doesNotSaveOrPublish_whenDuplicateSubmissionGuardThrows() {
        Meeting meeting = meeting();
        MeetingMember host = member(MeetingMemberRole.HOST);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(host));
        doThrow(new BusinessException(ErrorCode.DUPLICATE_SUBMISSION))
                .when(duplicateSubmissionGuard)
                .guard("recruit:create:" + USER_ID + ":" + MEETING_ID);

        assertThatThrownBy(
                        () ->
                                meetingRecruitService.createRecruit(
                                        MEETING_ID, createRequest(30, false, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_SUBMISSION);

        verify(postRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(Mockito.any(Object.class));
    }

    @Test
    @DisplayName("정원·기간이 열려 있고 미신청이면 신청되고 현황이 1 증가한다")
    void toggleParticipation_appliesWhenOpen() {
        stubMemberAndRecruitPost(true, openRecruit(4));
        when(participationRepository.countByPostIdAndStatusIn(eq(POST_ID), any()))
                .thenReturn(2L, 3L);
        when(participationRepository.findByPostIdAndUserId(POST_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(participationRepository.save(any(MeetingRecruitParticipation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RecruitParticipationResponse response =
                meetingRecruitService.toggleParticipation(MEETING_ID, POST_ID);

        assertThat(response.participationStatus())
                .isEqualTo(MeetingRecruitParticipationStatus.APPLIED);
        assertThat(response.participationAction()).isEqualTo(RecruitParticipationAction.CANCEL);
        assertThat(response.appliedCount()).isEqualTo(3);
        verify(participationRepository).save(any(MeetingRecruitParticipation.class));
    }

    @Test
    @DisplayName("이미 신청했으면 취소되고 현황이 1 감소한다")
    void toggleParticipation_cancelsWhenApplied() {
        stubMemberAndRecruitPost(true, openRecruit(4));
        MeetingRecruitParticipation participation =
                participation(MeetingRecruitParticipationStatus.APPLIED);
        when(participationRepository.findByPostIdAndUserId(POST_ID, USER_ID))
                .thenReturn(Optional.of(participation));
        when(participationRepository.countByPostIdAndStatusIn(eq(POST_ID), any())).thenReturn(2L);

        RecruitParticipationResponse response =
                meetingRecruitService.toggleParticipation(MEETING_ID, POST_ID);

        assertThat(response.participationStatus())
                .isEqualTo(MeetingRecruitParticipationStatus.CANCELLED);
        assertThat(response.participationAction()).isEqualTo(RecruitParticipationAction.APPLY);
        assertThat(response.appliedCount()).isEqualTo(2);
        verify(participation).cancel();
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
        when(participationRepository.findByPostIdAndUserId(POST_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(participationRepository.countByPostIdAndStatusIn(eq(POST_ID), any())).thenReturn(4L);

        assertThatThrownBy(() -> meetingRecruitService.toggleParticipation(MEETING_ID, POST_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECRUIT_CAPACITY_EXCEEDED);
        verify(participationRepository, never()).save(any());
    }

    @Test
    @DisplayName("미가입자는 external=false인 모집공고 상세를 볼 수 없다")
    void getRecruit_rejectsNonMember() {
        Meeting meeting = meeting();
        Post post = recruitPost();
        MeetingRecruit recruit = openRecruit(10);
        when(meetingRepository.findByIdAndDeletedAtIsNull(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(postRepository.findByIdFetchUser(POST_ID)).thenReturn(Optional.of(post));
        when(meetingRecruitRepository.findByPostId(POST_ID)).thenReturn(Optional.of(recruit));
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
        when(participationRepository.countByPostIdAndStatusIn(eq(POST_ID), any())).thenReturn(2L);

        RecruitDetailResponse response =
                meetingRecruitService.updateRecruit(MEETING_ID, POST_ID, updateRequest(40));

        assertThat(response.maxParticipants()).isEqualTo(40);
        assertThat(response.appliedCount()).isEqualTo(2);
        assertThat(response.participationCondition()).isEqualTo("성인 및 청소년 단체 신청 가능(수정)");
        verify(post).update("6월 정기 활동 팀원 모집(수정)", "소개 수정");
        verify(eventPublisher, never()).publishEvent(Mockito.any(Object.class));
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
        when(participationRepository.countByPostIdAndStatusIn(eq(POST_ID), any())).thenReturn(5L);

        assertThatThrownBy(
                        () ->
                                meetingRecruitService.updateRecruit(
                                        MEETING_ID, POST_ID, updateRequest(3)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECRUIT_MAX_BELOW_APPLIED);
        verify(post, never()).update(Mockito.anyString(), Mockito.anyString());
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
        Mockito.lenient().when(meeting.getName()).thenReturn("한강공원 플로깅");
        Mockito.lenient().when(meeting.isPostingBased()).thenReturn(false);
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
        LocalDateTime start = LocalDateTime.now().plusDays(5).withHour(9).withMinute(0);
        return new RecruitCreateRequest(
                "6월 정기 활동 팀원 모집",
                "소개",
                REGION_ID,
                "서울 영등포구 여의도동",
                start,
                start.plusHours(3),
                maxParticipants,
                Set.of(PostingCategory.ENVIRONMENT),
                timeRecognized,
                recognizedMinutes,
                LocalDateTime.now().plusDays(3),
                false,
                "성인 및 청소년 단체 신청 가능");
    }

    private RecruitUpdateRequest updateRequest(int maxParticipants) {
        LocalDateTime start = LocalDateTime.now().plusDays(5).withHour(9).withMinute(0);
        return new RecruitUpdateRequest(
                "6월 정기 활동 팀원 모집(수정)",
                "소개 수정",
                REGION_ID,
                "서울 영등포구 여의도동",
                start,
                start.plusHours(3),
                maxParticipants,
                Set.of(PostingCategory.ENVIRONMENT),
                false,
                null,
                LocalDateTime.now().plusDays(3),
                false,
                "성인 및 청소년 단체 신청 가능(수정)");
    }

    /** 신청 마감 전(신청 가능)인 모집공고. */
    private MeetingRecruit openRecruit(int maxParticipants) {
        LocalDateTime start = LocalDateTime.now().plusDays(5);
        return MeetingRecruit.create(
                POST_ID,
                REGION_ID,
                "서울 영등포구 여의도동",
                start,
                start.plusHours(3),
                maxParticipants,
                false,
                null,
                LocalDateTime.now().plusDays(3),
                false,
                Set.of(PostingCategory.ENVIRONMENT),
                "성인 및 청소년 단체 신청 가능");
    }

    /** 신청 마감이 지난 모집공고. */
    private MeetingRecruit closedRecruit(int maxParticipants) {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        return MeetingRecruit.create(
                POST_ID,
                REGION_ID,
                "서울 영등포구 여의도동",
                start,
                start.plusHours(3),
                maxParticipants,
                false,
                null,
                LocalDateTime.now().minusDays(1),
                false,
                Set.of(PostingCategory.ENVIRONMENT),
                "성인 및 청소년 단체 신청 가능");
    }
}
