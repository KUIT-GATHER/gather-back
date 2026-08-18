package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** V64가 만든 password_reset_token 스키마가 설계와 일치하는지 실제 DB 메타데이터로 확인한다. */
@SpringBootTest
@Transactional
class PasswordResetTokenSchemaIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("컬럼 타입과 token hash 컬럼의 ASCII binary 비교 설정을 유지한다")
    void columns_useExpectedTypesAndCollation() {
        Map<String, Object> userId = column("user_id");
        Map<String, Object> tokenHash = column("token_hash");
        Map<String, Object> expiresAt = column("expires_at");
        Map<String, Object> createdAt = column("created_at");

        assertThat(userId.get("COLUMN_TYPE")).isEqualTo("bigint");
        assertThat(userId.get("IS_NULLABLE")).isEqualTo("NO");
        assertThat(tokenHash.get("COLUMN_TYPE")).isEqualTo("varchar(64)");
        assertThat(tokenHash.get("COLLATION_NAME")).isEqualTo("ascii_bin");
        assertThat(tokenHash.get("IS_NULLABLE")).isEqualTo("NO");
        assertThat(expiresAt.get("DATETIME_PRECISION")).isEqualTo(6L);
        assertThat(expiresAt.get("IS_NULLABLE")).isEqualTo("NO");
        assertThat(createdAt.get("DATETIME_PRECISION")).isEqualTo(6L);
        assertThat(createdAt.get("IS_NULLABLE")).isEqualTo("NO");
    }

    @Test
    @DisplayName("사용자당 하나, token hash 전역 유일 제약과 만료 조회 인덱스를 갖는다")
    void indexes_enforceSingleActiveTokenAndSupportCleanup() {
        assertThat(nonUnique("uk_password_reset_token_user")).isZero();
        assertThat(nonUnique("uk_password_reset_token_token_hash")).isZero();
        assertThat(nonUnique("idx_password_reset_token_expires_at")).isEqualTo(1);
    }

    @Test
    @DisplayName("users 참조 FK는 삭제·수정 모두 RESTRICT다")
    void foreignKey_usesRestrictRules() {
        Map<String, Object> constraint =
                jdbcTemplate.queryForMap(
                        """
                        SELECT rc.DELETE_RULE, rc.UPDATE_RULE, kcu.REFERENCED_TABLE_NAME,
                               kcu.REFERENCED_COLUMN_NAME
                        FROM information_schema.referential_constraints rc
                        JOIN information_schema.key_column_usage kcu
                          ON kcu.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
                         AND kcu.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
                        WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
                          AND rc.CONSTRAINT_NAME = 'fk_password_reset_token_user'
                        """);

        assertThat(constraint.get("DELETE_RULE")).isEqualTo("RESTRICT");
        assertThat(constraint.get("UPDATE_RULE")).isEqualTo("RESTRICT");
        assertThat(constraint.get("REFERENCED_TABLE_NAME")).isEqualTo("users");
        assertThat(constraint.get("REFERENCED_COLUMN_NAME")).isEqualTo("id");
    }

    @Test
    @DisplayName("만료 시각이 생성 시각보다 늦어야 한다는 check 제약을 갖는다")
    void checkConstraint_requiresExpiryAfterCreation() {
        String checkClause =
                jdbcTemplate.queryForObject(
                        """
                        SELECT CHECK_CLAUSE
                        FROM information_schema.check_constraints
                        WHERE CONSTRAINT_SCHEMA = DATABASE()
                          AND CONSTRAINT_NAME = 'chk_password_reset_token_expiry'
                        """,
                        String.class);

        assertThat(checkClause).contains("expires_at").contains("created_at").contains(">");
    }

    private Map<String, Object> column(String columnName) {
        return jdbcTemplate.queryForMap(
                """
                SELECT COLUMN_TYPE, IS_NULLABLE, COLLATION_NAME, DATETIME_PRECISION
                FROM information_schema.columns
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'password_reset_token'
                  AND COLUMN_NAME = ?
                """,
                columnName);
    }

    private int nonUnique(String indexName) {
        return jdbcTemplate.queryForObject(
                """
                SELECT DISTINCT NON_UNIQUE
                FROM information_schema.statistics
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'password_reset_token'
                  AND INDEX_NAME = ?
                """,
                Integer.class,
                indexName);
    }
}
