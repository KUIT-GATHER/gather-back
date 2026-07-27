package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 익명화 대상 조회 조건 검증. 익명화 완료 여부를 별도 컬럼이 아니라 {@code phone_number} 접두사로 판정하는데, JPQL의 {@code like ...
 * escape} 표현은 {@code _}가 단일 문자 와일드카드라 잘못 쓰면 조용히 엉뚱한 행을 잡거나 놓친다. mock으로는 확인할 수 없어 실제 DB로 검증한다.
 */
@SpringBootTest
@Transactional
class AnonymizationTargetQueryTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RegionRepository regionRepository;

    private List<User> findTargets() {
        return userRepository.findAnonymizationTargets(
                UserStatus.WITHDRAWN, LocalDateTime.now().minusDays(7), PageRequest.of(0, 100));
    }

    @Test
    @DisplayName("유예가 끝났고 아직 익명화되지 않은 탈퇴 계정만 조회된다")
    void findAnonymizationTargets_returnsOnlyExpiredAndNotAnonymized() {
        User expired = save(user(), LocalDateTime.now().minusDays(8));
        User withinGrace = save(user(), LocalDateTime.now().minusDays(1));
        User active = save(user(), null);

        User alreadyAnonymized = save(user(), LocalDateTime.now().minusDays(8));
        alreadyAnonymized.anonymize();
        userRepository.flush();

        List<User> targets = findTargets();

        assertThat(targets).extracting(User::getId).contains(expired.getId());
        assertThat(targets)
                .extracting(User::getId)
                .doesNotContain(withinGrace.getId(), active.getId(), alreadyAnonymized.getId());
    }

    @Test
    @DisplayName("익명화한 계정은 다음 회차에서 다시 잡히지 않는다")
    void findAnonymizationTargets_afterAnonymize_excludesTheSameRow() {
        User target = save(user(), LocalDateTime.now().minusDays(8));
        assertThat(findTargets()).extracting(User::getId).contains(target.getId());

        target.anonymize();
        userRepository.flush();

        assertThat(findTargets()).extracting(User::getId).doesNotContain(target.getId());
    }

    private User save(User user, LocalDateTime withdrawnAt) {
        if (withdrawnAt != null) {
            user.withdraw(WithdrawalReason.SELF, withdrawnAt);
        }
        return userRepository.saveAndFlush(user);
    }

    private User user() {
        Region region =
                regionRepository.save(
                        Region.create("테스트구", 2, "999" + (System.nanoTime() % 10000000L), null));
        return User.create(
                "탈퇴자",
                LocalDate.of(1995, 1, 1),
                Gender.MALE,
                "010" + System.nanoTime() % 100000000L,
                null,
                null,
                "anon" + (System.nanoTime() % 10_000_000L),
                null,
                true,
                true,
                false,
                region,
                List.of());
    }
}
