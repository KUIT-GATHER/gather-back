package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** V65가 만든 email_verification.code_hash 스키마가 확장 전용 설계와 일치하는지 실제 DB 메타데이터로 확인한다. */
@SpringBootTest
@Transactional
class EmailVerificationCodeHashSchemaIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("code_hash는 ASCII binary 비교를 쓰는 nullable varchar(64)다")
    void codeHash_isNullableAsciiBinaryColumn() {
        Map<String, Object> codeHash = column("code_hash");

        assertThat(codeHash.get("COLUMN_TYPE")).isEqualTo("varchar(64)");
        assertThat(codeHash.get("CHARACTER_SET_NAME")).isEqualTo("ascii");
        assertThat(codeHash.get("COLLATION_NAME")).isEqualTo("ascii_bin");
        // 구 버전 JAR이 code_hash 없이 INSERT할 수 있어야 하므로 NOT NULL로 만들지 않는다.
        assertThat(codeHash.get("IS_NULLABLE")).isEqualTo("YES");
        assertThat(codeHash.get("COLUMN_DEFAULT")).isNull();
    }

    @Test
    @DisplayName("롤백 호환을 위해 기존 code 컬럼을 그대로 유지한다")
    void code_isKeptForRollbackCompatibility() {
        Map<String, Object> code = column("code");

        assertThat(code.get("COLUMN_TYPE")).isEqualTo("varchar(10)");
        assertThat(code.get("IS_NULLABLE")).isEqualTo("NO");
    }

    @Test
    @DisplayName("code_hash에는 인덱스를 만들지 않고 기존 인덱스는 유지한다")
    void codeHash_hasNoIndexAndExistingIndexesRemain() {
        assertThat(indexNames("code_hash")).isEmpty();
        assertThat(indexNames("email")).contains("uk_email_verification_email");
        assertThat(indexNames("verification_id")).contains("uk_email_verification_verification_id");
        assertThat(indexNames("created_at")).contains("idx_email_verification_created_at");
    }

    private Map<String, Object> column(String columnName) {
        return jdbcTemplate.queryForMap(
                """
                SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, CHARACTER_SET_NAME, COLLATION_NAME
                FROM information_schema.columns
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'email_verification'
                  AND COLUMN_NAME = ?
                """,
                columnName);
    }

    private List<String> indexNames(String columnName) {
        return jdbcTemplate.queryForList(
                """
                SELECT INDEX_NAME
                FROM information_schema.statistics
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'email_verification'
                  AND COLUMN_NAME = ?
                """,
                String.class,
                columnName);
    }
}
