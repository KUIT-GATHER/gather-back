package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PhoneVerificationPurposeUpgradeMigrationIntegrationTest {

    private static final String PREVIOUS_VERSION = "61";
    private static final String PURPOSE_MIGRATION_VERSION = "62";

    @Autowired private DataSourceProperties dataSourceProperties;

    @Test
    void migrateFromV61_backfillsExistingRowAndCreatesCleanupIndex() throws Exception {
        String databaseName =
                "gather_pv_upgrade_"
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
            insertLegacyVerification(upgradeUrl, username, password);

            Flyway flyway =
                    Flyway.configure()
                            .dataSource(upgradeUrl, username, password)
                            .locations("classpath:db/migration")
                            .load();
            flyway.migrate();

            // 이후 마이그레이션이 추가돼도 깨지지 않도록 최신 버전이 아니라 V62 적용 여부를 확인한다.
            assertThat(flyway.info().applied())
                    .extracting(
                            info ->
                                    info.getVersion() == null
                                            ? null
                                            : info.getVersion().getVersion())
                    .contains(PURPOSE_MIGRATION_VERSION);
            assertThat(queryPurpose(upgradeUrl, username, password)).isEqualTo("SIGNUP");
            assertThat(queryCleanupIndexCount(upgradeUrl, username, password, databaseName))
                    .isEqualTo(1);
        } finally {
            if (databaseCreated) {
                execute(adminUrl, username, password, "DROP DATABASE `" + databaseName + "`");
            }
        }
    }

    private void insertLegacyVerification(String url, String username, String password)
            throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 8, 16, 6, 45);
        try (Connection connection = DriverManager.getConnection(url, username, password);
                PreparedStatement statement =
                        connection.prepareStatement(
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
                                """)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, "01095550115");
            statement.setString(3, "GATHER-UPGRADE01");
            statement.setBoolean(4, false);
            statement.setTimestamp(5, Timestamp.valueOf(now.plusMinutes(5)));
            statement.setInt(6, 0);
            statement.setInt(7, 0);
            statement.setTimestamp(8, Timestamp.valueOf(now));
            statement.executeUpdate();
        }
    }

    private String queryPurpose(String url, String username, String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery("SELECT purpose FROM phone_verification LIMIT 1")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString("purpose");
        }
    }

    private int queryCleanupIndexCount(
            String url, String username, String password, String databaseName) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT COUNT(*)
                                FROM information_schema.statistics
                                WHERE table_schema = ?
                                  AND table_name = 'phone_verification'
                                  AND index_name = 'idx_phone_verification_created_at'
                                """)) {
            statement.setString(1, databaseName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1);
            }
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
