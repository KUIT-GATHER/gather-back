package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.scheduler.EmailVerificationLegacyPurgeRunner;
import com.gather.gather.domain.auth.service.EmailVerificationCleanupService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 평문 행 파기가 실제 삭제 조건과 보관 기간 정리 분리를 지키는지 실제 DB로 검증한다. */
@SpringBootTest
class EmailVerificationLegacyPurgeIntegrationTest {

    private static final String EMAIL_PREFIX = "legacy-purge-test-";
    private static final String CODE_HASH = "a".repeat(64);

    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private EmailVerificationCleanupService cleanupService;
    @Autowired private EmailVerificationLegacyPurgeRunner legacyPurgeRunner;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbcTemplate.update(
                "DELETE FROM email_verification WHERE email LIKE ?", EMAIL_PREFIX + "%");
    }

    @Test
    @DisplayName("code/code_hash 네 조합에서 엔티티 판정과 파기 쿼리 분류가 일치한다")
    void purgeLegacyVerifications_classifiesRowsSameAsEntityPredicate() {
        // 엔티티의 isLegacyFormat()과 파기 쿼리의 조건은 서로 다른 언어로 같은 규칙을 표현하므로,
        // 네 조합을 실제 DB에 넣고 두 판정이 갈라지지 않는지 한 테스트에서 함께 확인한다.
        String currentEmail = insertRow("current", "", CODE_HASH);
        String plaintextWithoutHashEmail = insertRow("plaintext-without-hash", "123456", null);
        String plaintextWithHashEmail = insertRow("plaintext-with-hash", "123456", CODE_HASH);
        String emptyCodeWithoutHashEmail = insertRow("empty-code-without-hash", "", null);

        assertThat(isLegacyFormat(currentEmail)).isFalse();
        assertThat(isLegacyFormat(plaintextWithoutHashEmail)).isTrue();
        assertThat(isLegacyFormat(plaintextWithHashEmail)).isTrue();
        assertThat(isLegacyFormat(emptyCodeWithoutHashEmail)).isTrue();

        int deletedCount = cleanupService.purgeLegacyVerifications();

        assertThat(deletedCount).isEqualTo(3);
        assertThat(emailVerificationRepository.findByEmail(currentEmail)).isPresent();
        assertThat(emailVerificationRepository.findByEmail(plaintextWithoutHashEmail)).isEmpty();
        assertThat(emailVerificationRepository.findByEmail(plaintextWithHashEmail)).isEmpty();
        assertThat(emailVerificationRepository.findByEmail(emptyCodeWithoutHashEmail)).isEmpty();
    }

    @Test
    @DisplayName("기동 러너가 평문 행을 파기한다")
    void startupRunner_purgesLegacyRows() {
        String plaintextEmail = insertRow("runner-plaintext", "123456", null);
        String currentEmail = insertRow("runner-current", "", CODE_HASH);

        legacyPurgeRunner.run(null);

        assertThat(emailVerificationRepository.findByEmail(plaintextEmail)).isEmpty();
        assertThat(emailVerificationRepository.findByEmail(currentEmail)).isPresent();
    }

    @Test
    @DisplayName("보관 기간 정리는 평문 여부와 무관하게 기존 기준만 적용한다")
    void retentionCleanup_isNotAffectedByLegacyCondition() {
        String recentLegacyEmail = insertRow("retention-recent-legacy", "123456", null);
        String recentCurrentEmail = insertRow("retention-recent-current", "", CODE_HASH);

        int deletedCount = cleanupService.cleanupOverdueVerifications();

        // 방금 만든 행은 보관 기간을 넘기지 않았으므로 평문이어도 보관 기간 정리 대상이 아니다.
        assertThat(emailVerificationRepository.findByEmail(recentLegacyEmail)).isPresent();
        assertThat(emailVerificationRepository.findByEmail(recentCurrentEmail)).isPresent();
        assertThat(deletedCount).isNotNegative();
    }

    private boolean isLegacyFormat(String email) {
        return emailVerificationRepository.findByEmail(email).orElseThrow().isLegacyFormat();
    }

    private String insertRow(String suffix, String code, String codeHash) {
        String email = EMAIL_PREFIX + suffix + "@example.com";
        jdbcTemplate.update(
                """
                INSERT INTO email_verification
                    (version, email, verification_id, code, code_hash, verified,
                     expires_at, created_at, daily_send_count, attempt_count)
                VALUES (0, ?, ?, ?, ?, 0, ?, ?, 1, 0)
                """,
                email,
                UUID.randomUUID().toString(),
                code,
                codeHash,
                LocalDateTime.now().plusMinutes(10),
                LocalDateTime.now());
        return email;
    }
}
