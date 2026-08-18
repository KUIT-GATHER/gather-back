package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
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
    private static final String LATEST_VERSION = "63";
    private static final String MIGRATION_TEST_URL_ENV = "GATHER_MIGRATION_TEST_URL";
    private static final String MIGRATION_TEST_USERNAME_ENV = "GATHER_MIGRATION_TEST_USERNAME";
    private static final String MIGRATION_TEST_PASSWORD_ENV = "GATHER_MIGRATION_TEST_PASSWORD";

    @Autowired private DataSourceProperties dataSourceProperties;

    @Test
    void migrateFromV61_backfillsExistingRowAndCreatesCleanupIndexes() throws Exception {
        MigrationDatabase migrationDatabase = createMigrationDatabase();
        boolean prepared = false;
        try {
            reset(migrationDatabase);
            Flyway.configure()
                    .dataSource(
                            migrationDatabase.url(),
                            migrationDatabase.username(),
                            migrationDatabase.password())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion(PREVIOUS_VERSION))
                    .load()
                    .migrate();
            insertLegacyVerification(
                    migrationDatabase.url(),
                    migrationDatabase.username(),
                    migrationDatabase.password());

            Flyway flyway =
                    Flyway.configure()
                            .dataSource(
                                    migrationDatabase.url(),
                                    migrationDatabase.username(),
                                    migrationDatabase.password())
                            .locations("classpath:db/migration")
                            .load();
            flyway.migrate();
            prepared = true;

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo(LATEST_VERSION);
            assertThat(
                            queryPurpose(
                                    migrationDatabase.url(),
                                    migrationDatabase.username(),
                                    migrationDatabase.password()))
                    .isEqualTo("SIGNUP");
            assertThat(
                            queryPhoneCleanupIndexCount(
                                    migrationDatabase.url(),
                                    migrationDatabase.username(),
                                    migrationDatabase.password(),
                                    migrationDatabase.databaseName()))
                    .isEqualTo(1);
            assertThat(
                            queryEmailCleanupIndexCount(
                                    migrationDatabase.url(),
                                    migrationDatabase.username(),
                                    migrationDatabase.password(),
                                    migrationDatabase.databaseName()))
                    .isEqualTo(1);
        } finally {
            migrationDatabase.close(prepared);
        }
    }

    @Test
    void migrateFreshToV63_createsEmailCleanupIndex() throws Exception {
        MigrationDatabase migrationDatabase = createMigrationDatabase();
        boolean prepared = false;
        try {
            reset(migrationDatabase);
            Flyway flyway =
                    Flyway.configure()
                            .dataSource(
                                    migrationDatabase.url(),
                                    migrationDatabase.username(),
                                    migrationDatabase.password())
                            .locations("classpath:db/migration")
                            .load();
            flyway.migrate();
            prepared = true;

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo(LATEST_VERSION);
            assertThat(
                            queryEmailCleanupIndexCount(
                                    migrationDatabase.url(),
                                    migrationDatabase.username(),
                                    migrationDatabase.password(),
                                    migrationDatabase.databaseName()))
                    .isEqualTo(1);
        } finally {
            migrationDatabase.close(prepared);
        }
    }

    private MigrationDatabase createMigrationDatabase() throws Exception {
        String sourceUrl = dataSourceProperties.determineUrl();
        String username = dataSourceProperties.determineUsername();
        String password = dataSourceProperties.determinePassword();
        Optional<String> migrationTestUrl = environmentValue(MIGRATION_TEST_URL_ENV);
        if (migrationTestUrl.isPresent()) {
            return new MigrationDatabase(
                    migrationTestUrl.orElseThrow(),
                    environmentValue(MIGRATION_TEST_USERNAME_ENV).orElse(username),
                    environmentValue(MIGRATION_TEST_PASSWORD_ENV).orElse(password),
                    databaseName(migrationTestUrl.orElseThrow()),
                    null);
        }

        String databaseName =
                "gather_pv_upgrade_"
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String adminUrl = replaceDatabase(sourceUrl, "mysql");
        execute(adminUrl, username, password, "CREATE DATABASE `" + databaseName + "`");
        return new MigrationDatabase(
                replaceDatabase(sourceUrl, databaseName),
                username,
                password,
                databaseName,
                adminUrl);
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

    private int queryPhoneCleanupIndexCount(
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

    private int queryEmailCleanupIndexCount(
            String url, String username, String password, String databaseName) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT COUNT(*)
                                FROM information_schema.statistics
                                WHERE table_schema = ?
                                  AND table_name = 'email_verification'
                                  AND index_name = 'idx_email_verification_created_at'
                                  AND column_name = 'created_at'
                                """)) {
            statement.setString(1, databaseName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1);
            }
        }
    }

    private void reset(MigrationDatabase migrationDatabase) {
        Flyway.configure()
                .dataSource(
                        migrationDatabase.url(),
                        migrationDatabase.username(),
                        migrationDatabase.password())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
    }

    private Optional<String> environmentValue(String name) {
        return Optional.ofNullable(System.getenv(name)).filter(value -> !value.isBlank());
    }

    private static void execute(String url, String username, String password, String sql)
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

    private String databaseName(String url) {
        String base = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        return base.substring(base.lastIndexOf('/') + 1);
    }

    private record MigrationDatabase(
            String url, String username, String password, String databaseName, String adminUrl) {

        void close(boolean prepared) throws Exception {
            if (adminUrl != null) {
                execute(adminUrl, username, password, "DROP DATABASE `" + databaseName + "`");
                return;
            }
            if (prepared) {
                Flyway.configure()
                        .dataSource(url, username, password)
                        .locations("classpath:db/migration")
                        .cleanDisabled(false)
                        .load()
                        .clean();
            }
        }
    }
}
