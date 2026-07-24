package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.posting.dto.PostingParticipationResponse;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostingParticipationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long POSTING_ID = 10L;
    private static final String EXT_ID = "3422497";
    private static final String EXPECTED_APPLICATION_URL =
            "https://1365.go.kr/vols/P9210/partcptn/timeCptn.do?type=show&progrmRegistNo=" + EXT_ID;

    @Mock private PostingParticipationRepository postingParticipationRepository;
    @Mock private PostingRepository postingRepository;

    private PostingParticipationService postingParticipationService;

    @BeforeEach
    void setUp() {
        postingParticipationService =
                new PostingParticipationService(postingParticipationRepository, postingRepository);
    }

    @Test
    @DisplayName("apply saves a participation and returns the 1365 application url")
    void apply_savesParticipation_whenPostingExistsAndNotDuplicate() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingRepository.findById(POSTING_ID)).thenReturn(Optional.of(posting()));
            when(postingParticipationRepository.existsByUserIdAndPostingId(USER_ID, POSTING_ID))
                    .thenReturn(false);
            when(postingParticipationRepository.saveAndFlush(any(PostingParticipation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            PostingParticipationResponse response = postingParticipationService.apply(POSTING_ID);

            assertThat(response.status()).isEqualTo(PostingParticipationStatus.APPLIED);
            assertThat(response.applicationUrl()).isEqualTo(EXPECTED_APPLICATION_URL);
        }
    }

    @Test
    @DisplayName("apply throws POSTING_NOT_FOUND when the posting does not exist")
    void apply_throwsPostingNotFound_whenPostingMissing() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingRepository.findById(POSTING_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postingParticipationService.apply(POSTING_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POSTING_NOT_FOUND);

            verify(postingParticipationRepository, never()).saveAndFlush(any());
        }
    }

    @Test
    @DisplayName("apply throws POSTING_CLOSED when the posting is not recruiting")
    void apply_throwsPostingClosed_whenPostingNotRecruiting() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingRepository.findById(POSTING_ID)).thenReturn(Optional.of(closedPosting()));

            assertThatThrownBy(() -> postingParticipationService.apply(POSTING_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POSTING_CLOSED);

            verify(postingParticipationRepository, never())
                    .existsByUserIdAndPostingId(any(), any());
            verify(postingParticipationRepository, never()).saveAndFlush(any());
        }
    }

    @Test
    @DisplayName(
            "apply throws POSTING_CLOSED when the posting is RECRUITING but was deactivated by"
                    + " expiry")
    void apply_throwsPostingClosed_whenRecruitingButDeactivated() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingRepository.findById(POSTING_ID))
                    .thenReturn(Optional.of(deactivatedRecruitingPosting()));

            assertThatThrownBy(() -> postingParticipationService.apply(POSTING_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POSTING_CLOSED);

            verify(postingParticipationRepository, never())
                    .existsByUserIdAndPostingId(any(), any());
            verify(postingParticipationRepository, never()).saveAndFlush(any());
        }
    }

    @Test
    @DisplayName("apply throws POSTING_APPLICATION_UNAVAILABLE when the posting has no extId")
    void apply_throwsPostingApplicationUnavailable_whenExtIdMissing() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingRepository.findById(POSTING_ID))
                    .thenReturn(Optional.of(postingWithoutExtId()));

            assertThatThrownBy(() -> postingParticipationService.apply(POSTING_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue(
                            "errorCode", ErrorCode.POSTING_APPLICATION_UNAVAILABLE);

            verify(postingParticipationRepository, never())
                    .existsByUserIdAndPostingId(any(), any());
            verify(postingParticipationRepository, never()).saveAndFlush(any());
        }
    }

    @Test
    @DisplayName("apply throws PARTICIPATION_DUPLICATE when already applied")
    void apply_throwsParticipationDuplicate_whenAlreadyApplied() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingRepository.findById(POSTING_ID)).thenReturn(Optional.of(posting()));
            when(postingParticipationRepository.existsByUserIdAndPostingId(USER_ID, POSTING_ID))
                    .thenReturn(true);

            assertThatThrownBy(() -> postingParticipationService.apply(POSTING_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPATION_DUPLICATE);

            verify(postingParticipationRepository, never()).saveAndFlush(any());
        }
    }

    @Test
    @DisplayName(
            "apply throws PARTICIPATION_DUPLICATE when a concurrent request wins the unique"
                    + " constraint race")
    void apply_throwsParticipationDuplicate_whenConcurrentInsertViolatesUniqueConstraint() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingRepository.findById(POSTING_ID)).thenReturn(Optional.of(posting()));
            when(postingParticipationRepository.existsByUserIdAndPostingId(USER_ID, POSTING_ID))
                    .thenReturn(false);
            DataIntegrityViolationException dbException =
                    new DataIntegrityViolationException(
                            "Duplicate entry '1-10' for key"
                                    + " 'uq_posting_participation_user_posting'");
            when(postingParticipationRepository.saveAndFlush(any(PostingParticipation.class)))
                    .thenThrow(dbException);

            assertThatThrownBy(() -> postingParticipationService.apply(POSTING_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPATION_DUPLICATE)
                    .hasCause(dbException);
        }
    }

    @Test
    @DisplayName(
            "apply rethrows the original exception when a DataIntegrityViolationException is"
                    + " unrelated to the participation unique constraint")
    void apply_rethrowsOriginalException_whenIntegrityViolationIsNotUniqueConstraint() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingRepository.findById(POSTING_ID)).thenReturn(Optional.of(posting()));
            when(postingParticipationRepository.existsByUserIdAndPostingId(USER_ID, POSTING_ID))
                    .thenReturn(false);
            DataIntegrityViolationException dbException =
                    new DataIntegrityViolationException(
                            "Cannot add or update a child row: a foreign key constraint fails");
            when(postingParticipationRepository.saveAndFlush(any(PostingParticipation.class)))
                    .thenThrow(dbException);

            assertThatThrownBy(() -> postingParticipationService.apply(POSTING_ID))
                    .isSameAs(dbException);
        }
    }

    @Test
    @DisplayName("cancel deletes the caller's own participation")
    void cancel_deletesParticipation_whenOwnedByCurrentUser() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            PostingParticipation participation = PostingParticipation.create(USER_ID, POSTING_ID);
            when(postingParticipationRepository.findByUserIdAndPostingId(USER_ID, POSTING_ID))
                    .thenReturn(Optional.of(participation));

            postingParticipationService.cancel(POSTING_ID);

            verify(postingParticipationRepository).delete(participation);
        }
    }

    @Test
    @DisplayName("cancel throws PARTICIPATION_NOT_FOUND when no participation exists for the user")
    void cancel_throwsParticipationNotFound_whenMissing() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findByUserIdAndPostingId(USER_ID, POSTING_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> postingParticipationService.cancel(POSTING_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPATION_NOT_FOUND);

            verify(postingParticipationRepository, never()).delete(any());
        }
    }

    @Test
    @DisplayName(
            "cancel throws PARTICIPATION_CANCEL_NOT_ALLOWED when the participation is COMPLETED")
    void cancel_throwsCancelNotAllowed_whenCompleted() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            PostingParticipation participation =
                    participationWithStatus(PostingParticipationStatus.COMPLETED);
            when(postingParticipationRepository.findByUserIdAndPostingId(USER_ID, POSTING_ID))
                    .thenReturn(Optional.of(participation));

            assertThatThrownBy(() -> postingParticipationService.cancel(POSTING_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue(
                            "errorCode", ErrorCode.PARTICIPATION_CANCEL_NOT_ALLOWED);

            verify(postingParticipationRepository, never()).delete(any());
        }
    }

    @Test
    @DisplayName(
            "cancel throws PARTICIPATION_CANCEL_NOT_ALLOWED when the participation is REVIEWED")
    void cancel_throwsCancelNotAllowed_whenReviewed() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            PostingParticipation participation =
                    participationWithStatus(PostingParticipationStatus.REVIEWED);
            when(postingParticipationRepository.findByUserIdAndPostingId(USER_ID, POSTING_ID))
                    .thenReturn(Optional.of(participation));

            assertThatThrownBy(() -> postingParticipationService.cancel(POSTING_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue(
                            "errorCode", ErrorCode.PARTICIPATION_CANCEL_NOT_ALLOWED);

            verify(postingParticipationRepository, never()).delete(any());
        }
    }

    @Test
    @DisplayName(
            "cancel deletes a CONFIRMED participation (cancel is still allowed pre-completion)")
    void cancel_deletesParticipation_whenConfirmed() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            PostingParticipation participation =
                    participationWithStatus(PostingParticipationStatus.CONFIRMED);
            when(postingParticipationRepository.findByUserIdAndPostingId(USER_ID, POSTING_ID))
                    .thenReturn(Optional.of(participation));

            postingParticipationService.cancel(POSTING_ID);

            verify(postingParticipationRepository).delete(participation);
        }
    }

    private PostingParticipation participationWithStatus(PostingParticipationStatus status) {
        PostingParticipation participation = PostingParticipation.create(USER_ID, POSTING_ID);
        ReflectionTestUtils.setField(participation, "status", status);
        return participation;
    }

    private Posting posting() {
        return Posting.builder()
                .extId(EXT_ID)
                .title("테스트 공고")
                .status(PostingStatus.RECRUITING)
                .activityDate(LocalDate.of(2026, 7, 15))
                .category(PostingCategory.ENVIRONMENT)
                .isActive(true)
                .build();
    }

    private Posting closedPosting() {
        return Posting.builder()
                .extId(EXT_ID)
                .title("테스트 공고")
                .status(PostingStatus.CLOSED)
                .activityDate(LocalDate.of(2026, 7, 15))
                .category(PostingCategory.ENVIRONMENT)
                .isActive(true)
                .build();
    }

    private Posting postingWithoutExtId() {
        return Posting.builder()
                .title("테스트 공고")
                .status(PostingStatus.RECRUITING)
                .activityDate(LocalDate.of(2026, 7, 15))
                .category(PostingCategory.ENVIRONMENT)
                .isActive(true)
                .build();
    }

    /** status=RECRUITING이지만 활동 종료일이 지나 deactivateExpired()로 isActive만 false가 된 공고. */
    private Posting deactivatedRecruitingPosting() {
        return Posting.builder()
                .extId(EXT_ID)
                .title("테스트 공고")
                .status(PostingStatus.RECRUITING)
                .activityDate(LocalDate.of(2026, 7, 15))
                .category(PostingCategory.ENVIRONMENT)
                .isActive(false)
                .build();
    }
}
