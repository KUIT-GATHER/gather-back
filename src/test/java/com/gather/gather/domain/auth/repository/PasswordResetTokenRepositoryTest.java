package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.PasswordResetToken;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.posting.entity.PostingCategory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PasswordResetTokenRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 17, 12, 0);

    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("token hash로 토큰과 사용자 ID를 조회한다")
    void findByTokenHash_returnsTokenAndUserId() {
        User user = saveUser("01091000001", "resettokenone");
        PasswordResetToken token = saveToken(user, "1".repeat(64), NOW.plusMinutes(10), NOW);

        assertThat(passwordResetTokenRepository.findByTokenHash("1".repeat(64)))
                .get()
                .extracting(PasswordResetToken::getId)
                .isEqualTo(token.getId());
        assertThat(passwordResetTokenRepository.findUserIdByTokenHash("1".repeat(64)))
                .contains(user.getId());
        assertThat(passwordResetTokenRepository.findUserIdByTokenHash("9".repeat(64))).isEmpty();
    }

    @Test
    @DisplayName("한 사용자는 활성 토큰을 하나만 가질 수 있다")
    void save_duplicateUser_isRejected() {
        User user = saveUser("01091000002", "resettokentwo");
        saveToken(user, "2".repeat(64), NOW.plusMinutes(10), NOW);

        assertThatThrownBy(
                        () ->
                                passwordResetTokenRepository.saveAndFlush(
                                        PasswordResetToken.issue(
                                                user, "3".repeat(64), NOW.plusMinutes(10), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("동일 token hash는 서로 다른 사용자에도 하나만 저장된다")
    void save_duplicateTokenHash_isRejected() {
        User firstUser = saveUser("01091000003", "resettokenthree");
        User secondUser = saveUser("01091000004", "resettokenfour");
        saveToken(firstUser, "4".repeat(64), NOW.plusMinutes(10), NOW);

        assertThatThrownBy(
                        () ->
                                passwordResetTokenRepository.saveAndFlush(
                                        PasswordResetToken.issue(
                                                secondUser,
                                                "4".repeat(64),
                                                NOW.plusMinutes(10),
                                                NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 ID로는 토큰 행을 만들 수 없다")
    void insert_unknownUser_violatesForeignKey() {
        assertThatThrownBy(() -> insertRow(-1L, "5".repeat(64), NOW.plusMinutes(10), NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("만료 시각이 생성 시각보다 이르거나 같은 행은 DB check 제약으로 거부된다")
    void insert_expiresAtNotAfterCreatedAt_violatesCheckConstraint() {
        User user = saveUser("01091000005", "resettokenfive");
        userRepository.flush();

        // MySQL check 위반(3819)은 무결성 위반으로 분류되지 않아 제약 이름으로 확인한다.
        assertThatThrownBy(() -> insertRow(user.getId(), "6".repeat(64), NOW, NOW))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_password_reset_token_expiry");
        assertThatThrownBy(() -> insertRow(user.getId(), "7".repeat(64), NOW.minusMinutes(1), NOW))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_password_reset_token_expiry");
    }

    @Test
    @DisplayName("사용자별 토큰을 일괄 삭제한다")
    void deleteAllByUserId_deletesOnlyOwnTokens() {
        User owner = saveUser("01091000006", "resettokensix");
        User other = saveUser("01091000007", "resettokenseven");
        saveToken(owner, "8".repeat(64), NOW.plusMinutes(10), NOW);
        PasswordResetToken otherToken = saveToken(other, "9".repeat(64), NOW.plusMinutes(10), NOW);

        int deleted = passwordResetTokenRepository.deleteAllByUserId(owner.getId());

        assertThat(deleted).isEqualTo(1);
        assertThat(passwordResetTokenRepository.findByUserId(owner.getId())).isEmpty();
        assertThat(passwordResetTokenRepository.existsById(otherToken.getId())).isTrue();
        assertThat(passwordResetTokenRepository.deleteAllByUserId(owner.getId())).isZero();
    }

    @Test
    @DisplayName("만료 시각이 기준 시각과 같거나 이른 토큰만 삭제한다")
    void deleteAllExpiredAtOrBefore_deletesExpiredTokensOnly() {
        User expiredOwner = saveUser("01091000008", "resettokeneight");
        User boundaryOwner = saveUser("01091000009", "resettokennine");
        User validOwner = saveUser("01091000010", "resettokenten");
        PasswordResetToken expired =
                saveToken(expiredOwner, "a".repeat(64), NOW.minusMinutes(1), NOW.minusMinutes(20));
        PasswordResetToken boundary =
                saveToken(boundaryOwner, "b".repeat(64), NOW, NOW.minusMinutes(10));
        PasswordResetToken valid = saveToken(validOwner, "c".repeat(64), NOW.plusMinutes(10), NOW);

        int deleted = passwordResetTokenRepository.deleteAllExpiredAtOrBefore(NOW);

        assertThat(deleted).isEqualTo(2);
        assertThat(passwordResetTokenRepository.existsById(expired.getId())).isFalse();
        assertThat(passwordResetTokenRepository.existsById(boundary.getId())).isFalse();
        assertThat(passwordResetTokenRepository.existsById(valid.getId())).isTrue();
        assertThat(passwordResetTokenRepository.deleteAllExpiredAtOrBefore(NOW)).isZero();
    }

    private void insertRow(
            Long userId, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO password_reset_token (user_id, token_hash, expires_at, created_at)
                VALUES (?, ?, ?, ?)
                """,
                userId,
                tokenHash,
                expiresAt,
                createdAt);
    }

    private PasswordResetToken saveToken(
            User user, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt) {
        return passwordResetTokenRepository.saveAndFlush(
                PasswordResetToken.issue(user, tokenHash, expiresAt, createdAt));
    }

    private User saveUser(String phoneNumber, String nickname) {
        return userRepository.saveAndFlush(
                User.create(
                        "홍길동",
                        LocalDate.of(2002, 3, 15),
                        Gender.MALE,
                        phoneNumber,
                        nickname + "@example.com",
                        "encoded-password",
                        nickname,
                        null,
                        true,
                        true,
                        false,
                        null,
                        List.of(PostingCategory.WELFARE)));
    }
}
