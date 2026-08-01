package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTask;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTaskStatus;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialAccountLinkStatus;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.kakao.service.LockedPendingSocialSignupSessions;
import com.gather.gather.domain.auth.kakao.service.SocialSignupSessionService;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.domain.auth.repository.KakaoUnlinkTaskRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.SocialAccountIdentitySnapshot;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.user.service.ProfileImageDeletionService;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountTerminationServiceTest {

    private static final Long USER_ID = 41L;
    private static final Long SOCIAL_ACCOUNT_ID = 73L;
    private static final long GENERATION = 1L;
    private static final String PROVIDER_KEY = "a".repeat(64);
    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-31T05:25:56.123456Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);

    @Mock private UserRepository userRepository;
    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private SocialSignupSessionService signupSessionService;
    @Mock private LockedPendingSocialSignupSessions lockedSignupSessions;
    @Mock private AccountRejoinBlockService rejoinBlockService;
    @Mock private AccountIdentityGuardService identityGuardService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private EmailVerificationRepository emailVerificationRepository;
    @Mock private KakaoUnlinkTaskRepository unlinkTaskRepository;
    @Mock private ProfileImageDeletionService profileImageDeletionService;

    private AccountTerminationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        service =
                new AccountTerminationService(
                        userRepository,
                        socialAccountRepository,
                        signupSessionService,
                        rejoinBlockService,
                        identityGuardService,
                        refreshTokenRepository,
                        emailVerificationRepository,
                        unlinkTaskRepository,
                        profileImageDeletionService,
                        clock);
    }

    @Test
    @DisplayName("일반 회원 탈퇴는 동일한 UTC operation timestamp로 완료와 정리를 수행한다")
    void terminate_localAccount_completesWithOneOperationTimestamp() {
        User user = localUser();
        RejoinBlockIdentifier phoneIdentifier = phoneIdentifier();
        user.changeProfileImageKey("profiles/41/current.jpg");
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(socialAccountRepository.findIdentitySnapshotsByUserIdAndProvider(
                        USER_ID, SocialProvider.KAKAO))
                .thenReturn(List.of());
        when(identityGuardService.lockPhone("01012345678", NOW)).thenReturn(phoneIdentifier);

        AccountTerminationResult result = service.terminate(USER_ID, WithdrawalReason.SELF);

        assertThat(result.outcome()).isEqualTo(AccountTerminationOutcome.COMPLETED);
        assertThat(result.occurredAt()).isEqualTo(NOW);
        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getWithdrawnAt()).isEqualTo(NOW);
        assertThat(user.getAnonymizedAt()).isEqualTo(NOW);
        verify(rejoinBlockService).createOrExtendBlock(phoneIdentifier, USER_ID, NOW);
        verify(refreshTokenRepository).deleteAllByUserId(USER_ID);
        verify(emailVerificationRepository).deleteAllByEmail("member@example.com");
        verify(profileImageDeletionService)
                .scheduleDeletion(USER_ID, "profiles/41/current.jpg", NOW);
        verify(unlinkTaskRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("카카오 회원 탈퇴 접수는 세션·상태·block·task에 동일한 UTC 시각을 사용한다")
    void terminate_kakaoAccount_acceptsAtomicallyWithOneOperationTimestamp() {
        User user = socialUser();
        SocialAccount socialAccount = linkedSocialAccount(user);
        SocialAccountIdentitySnapshot snapshot = linkedSnapshot();
        RejoinBlockIdentifier phoneIdentifier = phoneIdentifier();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(socialAccountRepository.findIdentitySnapshotsByUserIdAndProvider(
                        USER_ID, SocialProvider.KAKAO))
                .thenReturn(List.of(snapshot));
        when(socialAccountRepository.findByIdForUpdate(SOCIAL_ACCOUNT_ID))
                .thenReturn(Optional.of(socialAccount));
        when(signupSessionService.lockPendingForIdentity(any(), any(), any()))
                .thenReturn(lockedSignupSessions);
        when(unlinkTaskRepository.saveAndFlush(any(KakaoUnlinkTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(identityGuardService.lockPhone("01012345678", NOW)).thenReturn(phoneIdentifier);

        AccountTerminationResult result = service.terminate(USER_ID, WithdrawalReason.SELF);

        assertThat(result.outcome()).isEqualTo(AccountTerminationOutcome.ACCEPTED);
        assertThat(result.occurredAt()).isEqualTo(NOW);
        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWAL_PENDING);
        assertThat(user.getWithdrawalReason()).isEqualTo(WithdrawalReason.SELF);
        assertThat(user.getWithdrawnAt()).isNull();
        assertThat(user.getAnonymizedAt()).isNull();
        assertThat(socialAccount.getLinkStatus()).isEqualTo(SocialAccountLinkStatus.UNLINK_PENDING);
        assertThat(socialAccount.getUpdatedAt()).isEqualTo(NOW);

        RejoinBlockIdentifier kakaoIdentifier =
                new RejoinBlockIdentifier(AccountRejoinBlockIdentifierType.KAKAO, PROVIDER_KEY, 1);
        verify(signupSessionService)
                .lockPendingForIdentity(SocialProvider.KAKAO, kakaoIdentifier, NOW);
        verify(lockedSignupSessions).cancelAll(NOW);
        verify(rejoinBlockService).createOrExtendBlock(phoneIdentifier, USER_ID, NOW);
        verify(rejoinBlockService).createOrExtendBlock(kakaoIdentifier, USER_ID, NOW);
        verify(refreshTokenRepository).deleteAllByUserId(USER_ID);
        verify(profileImageDeletionService, never()).scheduleDeletion(any(), any(), any());

        InOrder lockOrder =
                inOrder(
                        userRepository,
                        signupSessionService,
                        socialAccountRepository,
                        lockedSignupSessions);
        lockOrder.verify(userRepository).findByIdForUpdate(USER_ID);
        lockOrder
                .verify(signupSessionService)
                .lockPendingForIdentity(SocialProvider.KAKAO, kakaoIdentifier, NOW);
        lockOrder.verify(socialAccountRepository).findByIdForUpdate(SOCIAL_ACCOUNT_ID);
        lockOrder.verify(lockedSignupSessions).cancelAll(NOW);

        org.mockito.ArgumentCaptor<KakaoUnlinkTask> taskCaptor =
                org.mockito.ArgumentCaptor.forClass(KakaoUnlinkTask.class);
        verify(unlinkTaskRepository).saveAndFlush(taskCaptor.capture());
        KakaoUnlinkTask task = taskCaptor.getValue();
        assertThat(task.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.PENDING);
        assertThat(task.getAttemptCount()).isZero();
        assertThat(task.getGeneration()).isEqualTo(GENERATION);
        assertThat(task.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(task.getCreatedAt()).isEqualTo(NOW);
        assertThat(task.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("pending 중복 요청은 기존 task 시각만 반환하고 부수효과를 재생성하지 않는다")
    void terminate_pending_isIdempotent() {
        User user = socialUser();
        user.requestWithdrawal(WithdrawalReason.SELF, NOW);
        SocialAccount socialAccount = linkedSocialAccount(user);
        socialAccount.markUnlinkPending(NOW);
        SocialAccountIdentitySnapshot snapshot =
                new SocialAccountIdentitySnapshot(
                        SOCIAL_ACCOUNT_ID,
                        SocialProvider.KAKAO,
                        PROVIDER_KEY,
                        1,
                        SocialAccountLinkStatus.UNLINK_PENDING,
                        GENERATION);
        KakaoUnlinkTask task = KakaoUnlinkTask.pending(socialAccount, GENERATION, NOW);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(socialAccountRepository.findIdentitySnapshotsByUserIdAndProvider(
                        USER_ID, SocialProvider.KAKAO))
                .thenReturn(List.of(snapshot));
        when(unlinkTaskRepository.findBySocialAccountIdAndGeneration(SOCIAL_ACCOUNT_ID, GENERATION))
                .thenReturn(Optional.of(task));

        AccountTerminationResult result = service.terminate(USER_ID, WithdrawalReason.ADMIN);

        assertThat(result)
                .isEqualTo(new AccountTerminationResult(AccountTerminationOutcome.ACCEPTED, NOW));
        assertThat(user.getWithdrawalReason()).isEqualTo(WithdrawalReason.SELF);
        verify(signupSessionService, never()).lockPendingForIdentity(any(), any(), any());
        verify(identityGuardService, never()).lockPhone(any(), any());
        verify(rejoinBlockService, never()).createOrExtendBlock(any(), any(), any());
        verify(refreshTokenRepository, never()).deleteAllByUserId(any());
        verify(unlinkTaskRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("withdrawn 중복 요청은 최초 완료 시각과 사유를 유지하고 부수효과를 반복하지 않는다")
    void terminate_withdrawn_isIdempotent() {
        User user = localUser();
        user.changeProfileImageKey("profiles/41/original.jpg");
        user.withdraw(WithdrawalReason.SELF, NOW);
        user.anonymize(NOW);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(socialAccountRepository.findIdentitySnapshotsByUserIdAndProvider(
                        USER_ID, SocialProvider.KAKAO))
                .thenReturn(List.of());

        AccountTerminationResult result = service.terminate(USER_ID, WithdrawalReason.ADMIN);

        assertThat(result)
                .isEqualTo(new AccountTerminationResult(AccountTerminationOutcome.COMPLETED, NOW));
        assertThat(user.getWithdrawalReason()).isEqualTo(WithdrawalReason.SELF);
        assertThat(user.getAnonymizedAt()).isEqualTo(NOW);
        verify(identityGuardService, never()).lockPhone(any(), any());
        verify(rejoinBlockService, never()).createOrExtendBlock(any(), any(), any());
        verify(profileImageDeletionService, never()).scheduleDeletion(any(), any(), any());
        verify(unlinkTaskRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("pending User에 identity snapshot이 없으면 invariant 오류이고 부수효과가 없다")
    void terminate_pendingWithoutSnapshot_rejectsWithoutSideEffects() {
        assertPendingConflict(null, null);
    }

    @Test
    @DisplayName("pending User의 SocialAccount가 LINKED이면 invariant 오류이고 부수효과가 없다")
    void terminate_pendingLinkedSocialAccount_rejectsWithoutSideEffects() {
        assertPendingConflict(pendingSnapshot(SocialAccountLinkStatus.LINKED, GENERATION), null);
    }

    @Test
    @DisplayName("pending User의 generation이 없거나 유효하지 않으면 invariant 오류이고 부수효과가 없다")
    void terminate_pendingInvalidGeneration_rejectsWithoutSideEffects() {
        assertPendingConflict(pendingSnapshot(SocialAccountLinkStatus.UNLINK_PENDING, null), null);
    }

    @Test
    @DisplayName("pending User의 generation이 0이면 invariant 오류이고 부수효과가 없다")
    void terminate_pendingZeroGeneration_rejectsWithoutSideEffects() {
        assertPendingConflict(pendingSnapshot(SocialAccountLinkStatus.UNLINK_PENDING, 0L), null);
    }

    @Test
    @DisplayName("pending User에 같은 generation task가 없으면 invariant 오류이고 부수효과가 없다")
    void terminate_pendingWithoutTask_rejectsWithoutSideEffects() {
        assertPendingConflict(
                pendingSnapshot(SocialAccountLinkStatus.UNLINK_PENDING, GENERATION), null);
    }

    @Test
    @DisplayName("pending User와 task의 SocialAccount 사용자가 다르면 invariant 오류이고 부수효과가 없다")
    void terminate_pendingTaskUserMismatch_rejectsWithoutSideEffects() {
        User otherUser = socialUser();
        ReflectionTestUtils.setField(otherUser, "id", USER_ID + 1);
        SocialAccount otherAccount = linkedSocialAccount(otherUser);
        otherAccount.markUnlinkPending(NOW);
        KakaoUnlinkTask task = KakaoUnlinkTask.pending(otherAccount, GENERATION, NOW);
        assertPendingConflict(
                pendingSnapshot(SocialAccountLinkStatus.UNLINK_PENDING, GENERATION), task);
    }

    @Test
    @DisplayName("ACTIVE User와 UNLINKED SocialAccount 조합은 일반 회원으로 처리하지 않는다")
    void terminate_rejectsInvalidActiveUnlinkedCombination() {
        User user = socialUser();
        SocialAccountIdentitySnapshot snapshot =
                new SocialAccountIdentitySnapshot(
                        SOCIAL_ACCOUNT_ID,
                        SocialProvider.KAKAO,
                        PROVIDER_KEY,
                        1,
                        SocialAccountLinkStatus.UNLINKED,
                        GENERATION);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(socialAccountRepository.findIdentitySnapshotsByUserIdAndProvider(
                        USER_ID, SocialProvider.KAKAO))
                .thenReturn(List.of(snapshot));

        assertThatThrownBy(() -> service.terminate(USER_ID, WithdrawalReason.SELF))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_TERMINATION_STATE_CONFLICT);
    }

    private User localUser() {
        User user =
                User.create(
                        "회원",
                        null,
                        null,
                        "01012345678",
                        "member@example.com",
                        "encoded-password",
                        "member41",
                        null,
                        true,
                        true,
                        false,
                        null,
                        List.of());
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private User socialUser() {
        User user =
                User.createSocial(
                        "회원",
                        null,
                        null,
                        "01012345678",
                        "member41",
                        null,
                        true,
                        true,
                        false,
                        null,
                        List.of());
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private SocialAccount linkedSocialAccount(User user) {
        SocialAccount socialAccount =
                SocialAccount.createLinked(
                        user,
                        SocialProvider.KAKAO,
                        "123456789",
                        PROVIDER_KEY,
                        1,
                        new EncryptedProviderUserId("ciphertext", 1),
                        NOW.minusDays(1));
        ReflectionTestUtils.setField(socialAccount, "id", SOCIAL_ACCOUNT_ID);
        return socialAccount;
    }

    private SocialAccountIdentitySnapshot linkedSnapshot() {
        return new SocialAccountIdentitySnapshot(
                SOCIAL_ACCOUNT_ID,
                SocialProvider.KAKAO,
                PROVIDER_KEY,
                1,
                SocialAccountLinkStatus.LINKED,
                GENERATION);
    }

    private SocialAccountIdentitySnapshot pendingSnapshot(
            SocialAccountLinkStatus linkStatus, Long generation) {
        return new SocialAccountIdentitySnapshot(
                SOCIAL_ACCOUNT_ID, SocialProvider.KAKAO, PROVIDER_KEY, 1, linkStatus, generation);
    }

    private void assertPendingConflict(
            SocialAccountIdentitySnapshot snapshot, KakaoUnlinkTask task) {
        User user = socialUser();
        user.requestWithdrawal(WithdrawalReason.SELF, NOW);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(socialAccountRepository.findIdentitySnapshotsByUserIdAndProvider(
                        USER_ID, SocialProvider.KAKAO))
                .thenReturn(snapshot == null ? List.of() : List.of(snapshot));
        if (snapshot != null
                && snapshot.linkStatus() == SocialAccountLinkStatus.UNLINK_PENDING
                && snapshot.generation() != null
                && snapshot.generation() > 0) {
            when(unlinkTaskRepository.findBySocialAccountIdAndGeneration(
                            snapshot.id(), snapshot.generation()))
                    .thenReturn(Optional.ofNullable(task));
        }

        assertThatThrownBy(() -> service.terminate(USER_ID, WithdrawalReason.ADMIN))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_TERMINATION_STATE_CONFLICT);
        assertThat(user.getWithdrawalReason()).isEqualTo(WithdrawalReason.SELF);
        verify(identityGuardService, never()).lockPhone(any(), any());
        verify(rejoinBlockService, never()).createOrExtendBlock(any(), any(), any());
        verify(refreshTokenRepository, never()).deleteAllByUserId(any());
        verify(unlinkTaskRepository, never()).saveAndFlush(any());
    }

    private RejoinBlockIdentifier phoneIdentifier() {
        return new RejoinBlockIdentifier(AccountRejoinBlockIdentifierType.PHONE, "b".repeat(64), 1);
    }
}
