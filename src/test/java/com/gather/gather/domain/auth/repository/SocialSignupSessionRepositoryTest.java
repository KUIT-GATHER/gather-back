package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.SocialSignupSession;
import com.gather.gather.domain.auth.entity.SocialSignupSessionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SocialSignupSessionRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 30, 12, 0);
    private static final String PROVIDER_USER_KEY = "d".repeat(64);

    @Autowired private SocialSignupSessionRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @PersistenceContext private EntityManager entityManager;

    @Test
    @DisplayName("token hash로 원문 없이 가입 세션을 정확히 조회한다")
    void findByTokenHash_returnsExactSessionWithoutRawToken() {
        String tokenHash = "a".repeat(64);
        repository.saveAndFlush(session(tokenHash));

        SocialSignupSession found = repository.findByTokenHash(tokenHash).orElseThrow();

        assertThat(found.getTokenHash()).isEqualTo(tokenHash);
        assertThat(found.getTokenHash()).doesNotContain("signup-token");
    }

    @Test
    @DisplayName("동일 token hash는 UNIQUE 제약으로 거부한다")
    void save_duplicateTokenHash_isRejected() {
        String tokenHash = "b".repeat(64);
        repository.saveAndFlush(session(tokenHash));

        assertThatThrownBy(() -> repository.saveAndFlush(session(tokenHash)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("identity의 PENDING 세션을 id 고정 순서로 비관적 잠금 조회한다")
    void findAllByIdentityAndStatusForUpdate_returnsPendingInIdOrder() {
        SocialSignupSession first = repository.saveAndFlush(session("c".repeat(64)));
        SocialSignupSession second = repository.saveAndFlush(session("d".repeat(64)));
        SocialSignupSession terminal = repository.saveAndFlush(session("e".repeat(64)));
        terminal.consume(NOW.plusMinutes(1));
        repository.flush();

        List<SocialSignupSession> pending =
                repository.findAllByIdentityAndStatusForUpdate(
                        SocialProvider.KAKAO, PROVIDER_USER_KEY, SocialSignupSessionStatus.PENDING);

        assertThat(pending)
                .extracting(SocialSignupSession::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    @DisplayName("identity 일괄 취소는 PENDING만 바꾸고 terminal 상태를 보존한다")
    void cancelPendingByIdentity_doesNotChangeTerminalSessions() {
        SocialSignupSession pending = repository.saveAndFlush(session("f".repeat(64)));
        SocialSignupSession consumed = repository.saveAndFlush(session("1".repeat(64)));
        consumed.consume(NOW.plusMinutes(1));
        repository.flush();

        int changed =
                repository.cancelPendingByIdentity(
                        SocialProvider.KAKAO, PROVIDER_USER_KEY, NOW.plusMinutes(2));
        entityManager.clear();

        assertThat(changed).isEqualTo(1);
        assertThat(repository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(SocialSignupSessionStatus.CANCELLED);
        assertThat(repository.findById(consumed.getId()).orElseThrow().getStatus())
                .isEqualTo(SocialSignupSessionStatus.CONSUMED);
    }

    @Test
    @DisplayName("V34는 token UNIQUE와 identity·expiresAt 인덱스를 생성한다")
    void migration_createsRequiredIndexes() {
        List<String> indexes =
                jdbcTemplate.queryForList(
                        """
                        SELECT DISTINCT index_name
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'social_signup_session'
                        """,
                        String.class);

        assertThat(indexes)
                .contains(
                        "uk_social_signup_session_token_hash",
                        "idx_social_signup_session_provider_key_status",
                        "idx_social_signup_session_expires_at");
    }

    private SocialSignupSession session(String tokenHash) {
        return SocialSignupSession.create(
                tokenHash,
                SocialProvider.KAKAO,
                PROVIDER_USER_KEY,
                3,
                new EncryptedProviderUserId("ciphertext", 4),
                NOW.plusMinutes(15),
                NOW);
    }
}
