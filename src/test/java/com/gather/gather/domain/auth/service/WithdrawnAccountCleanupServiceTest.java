package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.kakao.service.KakaoUnlinkService;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WithdrawnAccountCleanupServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private UserRepository userRepository;
    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private KakaoUnlinkService kakaoUnlinkService;

    private final WithdrawalPolicy withdrawalPolicy = new WithdrawalPolicy();

    private WithdrawnAccountCleanupService cleanupService() {
        return new WithdrawnAccountCleanupService(
                userRepository, socialAccountRepository, kakaoUnlinkService, withdrawalPolicy);
    }

    private User withdrawnUser(LocalDateTime withdrawnAt) {
        User user =
                User.create(
                        "홍길동",
                        LocalDate.of(2000, 1, 1),
                        Gender.MALE,
                        "01012345678",
                        "test@example.com",
                        "encoded-password",
                        "길동",
                        null,
                        true,
                        true,
                        false,
                        Region.create("강남구", 2, "11680", null),
                        List.of(PostingCategory.WELFARE));
        ReflectionTestUtils.setField(user, "id", USER_ID);
        if (withdrawnAt != null) {
            user.withdraw(WithdrawalReason.SELF, withdrawnAt);
        }
        return user;
    }

    private SocialAccount socialAccountOf(User user) {
        return SocialAccount.create(user, SocialProvider.KAKAO, "4242");
    }

    private void givenPendingUnlink(SocialAccount account) {
        when(socialAccountRepository.findByUserStatus(
                        eq(UserStatus.WITHDRAWN), any(Pageable.class)))
                .thenReturn(List.of(account));
    }

    @Test
    @DisplayName("유예가 끝난 계정을 익명화하고 건수를 돌려준다")
    void anonymizeExpiredAccounts_anonymizesTargets() {
        User target = withdrawnUser(LocalDateTime.now().minusDays(8));
        when(userRepository.findAnonymizationTargets(
                        eq(UserStatus.WITHDRAWN), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(target));

        int count = cleanupService().anonymizeExpiredAccounts();

        assertThat(count).isEqualTo(1);
        assertThat(target.getPhoneNumber()).isEqualTo("wd_" + USER_ID);
        assertThat(target.getNickname()).isEqualTo("wd_" + USER_ID);
        assertThat(target.getEmail()).isNull();
    }

    @Test
    @DisplayName("익명화 대상이 없으면 0건이다")
    void anonymizeExpiredAccounts_withoutTargets_returnsZero() {
        when(userRepository.findAnonymizationTargets(
                        eq(UserStatus.WITHDRAWN), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(cleanupService().anonymizeExpiredAccounts()).isZero();
    }

    @Test
    @DisplayName("유예 기간 안이면 카카오 연결 해제를 다시 시도한다")
    void retryPendingUnlinks_withinGracePeriod_retriesUnlink() {
        givenPendingUnlink(socialAccountOf(withdrawnUser(LocalDateTime.now().minusDays(3))));

        assertThat(cleanupService().retryPendingUnlinks()).isEqualTo(1);

        verify(kakaoUnlinkService).unlinkIfLinked(USER_ID);
        verify(socialAccountRepository, never()).delete(any());
    }

    @Test
    @DisplayName("유예가 지나도 남아 있으면 카카오를 더 호출하지 않고 연동 정보만 강제로 지운다")
    void retryPendingUnlinks_afterGracePeriod_forcesDeletion() {
        SocialAccount account = socialAccountOf(withdrawnUser(LocalDateTime.now().minusDays(8)));
        givenPendingUnlink(account);

        assertThat(cleanupService().retryPendingUnlinks()).isEqualTo(1);

        verifyNoInteractions(kakaoUnlinkService);
        verify(socialAccountRepository).delete(account);
    }

    @Test
    @DisplayName("탈퇴 시각이 없는 과거 계정은 강제 삭제하지 않고 재시도만 한다")
    void retryPendingUnlinks_withoutWithdrawnAt_onlyRetries() {
        User legacyUser = withdrawnUser(null);
        ReflectionTestUtils.setField(legacyUser, "status", UserStatus.WITHDRAWN);
        givenPendingUnlink(socialAccountOf(legacyUser));

        assertThat(cleanupService().retryPendingUnlinks()).isEqualTo(1);

        verify(kakaoUnlinkService).unlinkIfLinked(USER_ID);
        verify(socialAccountRepository, never()).delete(any());
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지 처리를 멈추지 않는다")
    void retryPendingUnlinks_whenOneFails_continues() {
        User failing = withdrawnUser(LocalDateTime.now().minusDays(3));
        User succeeding = withdrawnUser(LocalDateTime.now().minusDays(3));
        ReflectionTestUtils.setField(succeeding, "id", 8L);
        when(socialAccountRepository.findByUserStatus(
                        eq(UserStatus.WITHDRAWN), any(Pageable.class)))
                .thenReturn(List.of(socialAccountOf(failing), socialAccountOf(succeeding)));
        doThrow(new IllegalStateException("kakao down"))
                .when(kakaoUnlinkService)
                .unlinkIfLinked(USER_ID);

        assertThat(cleanupService().retryPendingUnlinks()).isEqualTo(1);

        verify(kakaoUnlinkService).unlinkIfLinked(8L);
    }

    @Test
    @DisplayName("재처리 대상이 없으면 카카오를 호출하지 않는다")
    void retryPendingUnlinks_withoutPendingRows_doesNothing() {
        when(socialAccountRepository.findByUserStatus(
                        eq(UserStatus.WITHDRAWN), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(cleanupService().retryPendingUnlinks()).isZero();

        verify(kakaoUnlinkService, never()).unlinkIfLinked(anyLong());
    }
}
