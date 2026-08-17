package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 기존 스키마에 V64를 얹는 업그레이드 경로를 일회용 데이터베이스로 검증한다.
 *
 * <p>V63(이메일 인증 파기 정책)은 별도 PR에 있어 이 브랜치에 없으므로, 직전 버전인 V62를 시작점으로 사용한다.
 */
@SpringBootTest
class PasswordResetTokenUpgradeMigrationIntegrationTest {

    private static final String PREVIOUS_VERSION = "62";
    private static final String PASSWORD_RESET_TOKEN_VERSION = "64";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 17, 12, 0);

    @Autowired private DataSourceProperties dataSourceProperties;

    @Test
    @DisplayName("V62까지 적용된 스키마에 V64를 적용하면 재설정 토큰 테이블과 제약이 만들어진다")
    void migrateFromV62_createsPasswordResetTokenTableWithConstraints() throws Exception {
        String databaseName =
                "gather_prt_upgrade_"
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String sourceUrl = dataSourceProperties.determineUrl();
        String adminUrl = replaceDatabase(sourceUrl, "mysql");
        String upgradeUrl = replaceDatabase(sourceUrl, databaseName);
        String username = dataSourceProperties.determineUsername();
        String password = dataSourceProperties.determinePassword();
        boolean databaseCreated = false;

        try {
            execute(adminUrl, username, password, "CREATE DATABASE `" + databaseName + "`");
            databaseCreated = true;
            Flyway.configure()
                    .dataSource(upgradeUrl, username, password)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion(PREVIOUS_VERSION))
                    .load()
                    .migrate();
            long userId = insertUser(upgradeUrl, username, password);

            Flyway flyway =
                    Flyway.configure()
                            .dataSource(upgradeUrl, username, password)
                            .locations("classpath:db/migration")
                            .load();
            flyway.migrate();

            // 이후 마이그레이션이 추가돼도 깨지지 않도록 최신 버전이 아니라 V64 적용 여부를 확인한다.
            assertThat(flyway.info().applied())
                    .extracting(
                            info ->
                                    info.getVersion() == null
                                            ? null
                                            : info.getVersion().getVersion())
                    .contains(PASSWORD_RESET_TOKEN_VERSION);
            insertToken(
                    upgradeUrl, username, password, userId, "a".repeat(64), NOW.plusMinutes(10));
            assertThat(tokenCount(upgradeUrl, username, password)).isEqualTo(1);

            assertThatThrownBy(
                            () ->
                                    insertToken(
                                            upgradeUrl,
                                            username,
                                            password,
                                            userId,
                                            "b".repeat(64),
                                            NOW.plusMinutes(10)))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(
                            () ->
                                    insertToken(
                                            upgradeUrl,
                                            username,
                                            password,
                                            userId + 1_000_000,
                                            "c".repeat(64),
                                            NOW.plusMinutes(10)))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(
                            () ->
                                    insertToken(
                                            upgradeUrl,
                                            username,
                                            password,
                                            userId,
                                            "d".repeat(64),
                                            NOW.minusMinutes(1)))
                    .isInstanceOf(SQLException.class);
            assertThat(tokenCount(upgradeUrl, username, password)).isEqualTo(1);
        } finally {
            if (databaseCreated) {
                execute(adminUrl, username, password, "DROP DATABASE `" + databaseName + "`");
            }
        }
    }

    private long insertUser(String url, String username, String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                INSERT INTO users (
                                    phone_number,
                                    nickname,
                                    role,
                                    status,
                                    email_verified,
                                    service_terms_agreed,
                                    privacy_policy_agreed,
                                    marketing_agreed
                                ) VALUES (?, ?, 'USER', 'ACTIVE', 1, 1, 1, 0)
                                """,
                                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "01095550201");
            statement.setString(2, "업그레이드");
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }

    private void insertToken(
            String url,
            String username,
            String password,
            long userId,
            String tokenHash,
            LocalDateTime expiresAt)
            throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                INSERT INTO password_reset_token (
                                    user_id, token_hash, expires_at, created_at
                                ) VALUES (?, ?, ?, ?)
                                """)) {
            statement.setLong(1, userId);
            statement.setString(2, tokenHash);
            statement.setTimestamp(3, Timestamp.valueOf(expiresAt));
            statement.setTimestamp(4, Timestamp.valueOf(NOW));
            statement.executeUpdate();
        }
    }

    private int tokenCount(String url, String username, String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery("SELECT COUNT(*) FROM password_reset_token")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private void execute(String url, String username, String password, String sql)
            throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String replaceDatabase(String sourceUrl, String databaseName) {
        int queryStart = sourceUrl.indexOf('?');
        String base = queryStart >= 0 ? sourceUrl.substring(0, queryStart) : sourceUrl;
        String query = queryStart >= 0 ? sourceUrl.substring(queryStart) : "";
        int databaseSeparator = base.lastIndexOf('/');
        if (databaseSeparator < "jdbc:mysql://".length()) {
            throw new IllegalStateException("MySQL datasource URL 형식이 올바르지 않습니다.");
        }
        return base.substring(0, databaseSeparator + 1) + databaseName + query;
    }
}
