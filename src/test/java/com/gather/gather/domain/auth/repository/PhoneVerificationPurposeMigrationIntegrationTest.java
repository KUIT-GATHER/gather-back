package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PhoneVerificationPurposeMigrationIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void databaseDefaultBackfillsLegacyInsertAsSignup() {
        String verificationId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.of(2026, 8, 16, 6, 45);
        jdbcTemplate.update(
                """
                INSERT INTO phone_verification (
                    verification_id,
                    phone_number,
                    verification_code,
                    verified,
                    expires_at,
                    confirm_attempt_count,
                    qr_request_count,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                verificationId,
                "01095550106",
                "GATHER-MIGRATION1",
                false,
                now.plusMinutes(5),
                0,
                0,
                now);

        String purpose =
                jdbcTemplate.queryForObject(
                        "select purpose from phone_verification where verification_id = ?",
                        String.class,
                        verificationId);

        assertThat(purpose).isEqualTo("SIGNUP");
        Integer indexCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'phone_verification'
                          AND index_name = 'idx_phone_verification_created_at'
                        """,
                        Integer.class);
        assertThat(indexCount).isEqualTo(1);
    }
}
