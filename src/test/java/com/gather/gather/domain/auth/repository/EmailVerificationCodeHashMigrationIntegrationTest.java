package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 기존 스키마에 V65를 얹는 확장 경로를 일회용 데이터베이스로 검증한다.
 *
 * <p>직전 버전인 V64를 시작점으로 사용한다.
 */
@SpringBootTest
class EmailVerificationCodeHashMigrationIntegrationTest {

    private static final String PREVIOUS_VERSION = "64";
    private static final String CODE_HASH_VERSION = "65";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 12, 0);

    @Autowired private DataSourceProperties dataSourceProperties;

    @Test
    @DisplayName("V64까지 적용된 스키마에 V65를 적용하면 기존 평문 행을 지우지 않고 code_hash만 추가한다")
    void migrateFromV64_addsCodeHashAndKeepsExistingPlaintextRows() throws Exception {
        withUpgradeDatabase(
                PREVIOUS_VERSION,
                (url, username, password) -> {
                    insertLegacyRow(url, username, password, "legacy@example.com", "123456");

                    Flyway flyway = flyway(url, username, password);
                    flyway.migrate();

                    // 이후 마이그레이션이 추가돼도 깨지지 않도록 최신 버전이 아니라 V65 적용 여부를 확인한다.
                    assertThat(flyway.info().applied())
                            .extracting(
                                    info ->
                                            info.getVersion() == null
                                                    ? null
                                                    : info.getVersion().getVersion())
                            .contains(CODE_HASH_VERSION);

                    // 마이그레이션은 확장만 하고 기존 평문 행 파기는 애플리케이션 기동 시점에 맡긴다.
                    Map<String, Object> row =
                            queryRow(
                                    url,
                                    username,
                                    password,
                                    "SELECT code, code_hash FROM email_verification"
                                            + " WHERE email = 'legacy@example.com'");
                    assertThat(row).isNotNull();
                    assertThat(row.get("code")).isEqualTo("123456");
                    assertThat(row.get("code_hash")).isNull();

                    assertCodeHashSchema(url, username, password);
                });
    }

    @Test
    @DisplayName("처음부터 전체 마이그레이션을 적용해도 같은 code_hash 스키마가 만들어진다")
    void migrateFromScratch_createsSameCodeHashSchema() throws Exception {
        withUpgradeDatabase(
                null,
                (url, username, password) -> {
                    assertCodeHashSchema(url, username, password);

                    // 새 애플리케이션이 쓰는 형식과 구 버전이 쓰던 형식이 모두 저장 가능해야 한다.
                    insertLegacyRow(url, username, password, "plaintext@example.com", "654321");
                    insertCurrentRow(url, username, password, "hashed@example.com", "a".repeat(64));
                });
    }

    private void assertCodeHashSchema(String url, String username, String password)
            throws SQLException {
        Map<String, Object> codeHash =
                queryRow(
                        url,
                        username,
                        password,
                        """
                        SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, CHARACTER_SET_NAME,
                               COLLATION_NAME
                        FROM information_schema.columns
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'email_verification'
                          AND COLUMN_NAME = 'code_hash'
                        """);
        assertThat(codeHash).isNotNull();
        assertThat(codeHash.get("COLUMN_TYPE")).isEqualTo("varchar(64)");
        assertThat(codeHash.get("IS_NULLABLE")).isEqualTo("YES");
        assertThat(codeHash.get("COLUMN_DEFAULT")).isNull();
        assertThat(codeHash.get("CHARACTER_SET_NAME")).isEqualTo("ascii");
        assertThat(codeHash.get("COLLATION_NAME")).isEqualTo("ascii_bin");

        Map<String, Object> code =
                queryRow(
                        url,
                        username,
                        password,
                        """
                        SELECT COLUMN_TYPE, IS_NULLABLE
                        FROM information_schema.columns
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'email_verification'
                          AND COLUMN_NAME = 'code'
                        """);
        assertThat(code).isNotNull();
        assertThat(code.get("COLUMN_TYPE")).isEqualTo("varchar(10)");
        assertThat(code.get("IS_NULLABLE")).isEqualTo("NO");

        Map<String, Object> codeHashIndex =
                queryRow(
                        url,
                        username,
                        password,
                        """
                        SELECT COUNT(*) AS index_count
                        FROM information_schema.statistics
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'email_verification'
                          AND COLUMN_NAME = 'code_hash'
                        """);
        assertThat(codeHashIndex).isNotNull();
        assertThat(((Number) codeHashIndex.get("index_count")).intValue()).isZero();

        Map<String, Object> existingIndexes =
                queryRow(
                        url,
                        username,
                        password,
                        """
                        SELECT COUNT(DISTINCT INDEX_NAME) AS index_count
                        FROM information_schema.statistics
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'email_verification'
                          AND INDEX_NAME IN ('uk_email_verification_email',
                                             'uk_email_verification_verification_id',
                                             'idx_email_verification_created_at')
                        """);
        assertThat(existingIndexes).isNotNull();
        assertThat(((Number) existingIndexes.get("index_count")).intValue()).isEqualTo(3);
    }

    static void insertLegacyRow(
            String url, String username, String password, String email, String code)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                INSERT INTO email_verification
                                    (version, email, verification_id, code, verified,
                                     expires_at, created_at, daily_send_count, attempt_count)
                                VALUES (0, ?, ?, ?, 0, ?, ?, 1, 0)
                                """)) {
            statement.setString(1, email);
            statement.setString(2, UUID.randomUUID().toString());
            statement.setString(3, code);
            statement.setTimestamp(4, Timestamp.valueOf(NOW.plusMinutes(10)));
            statement.setTimestamp(5, Timestamp.valueOf(NOW));
            statement.executeUpdate();
        }
    }

    static void insertCurrentRow(
            String url, String username, String password, String email, String codeHash)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                INSERT INTO email_verification
                                    (version, email, verification_id, code, code_hash, verified,
                                     expires_at, created_at, daily_send_count, attempt_count)
                                VALUES (0, ?, ?, '', ?, 0, ?, ?, 1, 0)
                                """)) {
            statement.setString(1, email);
            statement.setString(2, UUID.randomUUID().toString());
            statement.setString(3, codeHash);
            statement.setTimestamp(4, Timestamp.valueOf(NOW.plusMinutes(10)));
            statement.setTimestamp(5, Timestamp.valueOf(NOW));
            statement.executeUpdate();
        }
    }

    static Map<String, Object> queryRow(String url, String username, String password, String sql)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                return null;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            int columnCount = resultSet.getMetaData().getColumnCount();
            for (int index = 1; index <= columnCount; index++) {
                row.put(resultSet.getMetaData().getColumnLabel(index), resultSet.getObject(index));
            }
            return row;
        }
    }

    static Flyway flyway(String url, String username, String password) {
        return Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .load();
    }

    /** 일회용 데이터베이스를 만들어 {@code startVersion}까지 적용한 뒤 검증을 수행한다. */
    void withUpgradeDatabase(String startVersion, DatabaseCallback callback) throws Exception {
        withUpgradeDatabase(dataSourceProperties, "gather_evh_", startVersion, callback);
    }

    static void withUpgradeDatabase(
            DataSourceProperties dataSourceProperties,
            String databasePrefix,
            String startVersion,
            DatabaseCallback callback)
            throws Exception {
        String databaseName =
                databasePrefix + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String sourceUrl = dataSourceProperties.determineUrl();
        String adminUrl = replaceDatabase(sourceUrl, "mysql");
        String upgradeUrl = replaceDatabase(sourceUrl, databaseName);
        String username = dataSourceProperties.determineUsername();
        String password = dataSourceProperties.determinePassword();
        boolean databaseCreated = false;

        try {
            execute(adminUrl, username, password, "CREATE DATABASE `" + databaseName + "`");
            databaseCreated = true;
            if (startVersion == null) {
                flyway(upgradeUrl, username, password).migrate();
            } else {
                Flyway.configure()
                        .dataSource(upgradeUrl, username, password)
                        .locations("classpath:db/migration")
                        .target(MigrationVersion.fromVersion(startVersion))
                        .load()
                        .migrate();
            }
            callback.accept(upgradeUrl, username, password);
        } finally {
            if (databaseCreated) {
                execute(adminUrl, username, password, "DROP DATABASE `" + databaseName + "`");
            }
        }
    }

    static void execute(String url, String username, String password, String sql)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    static String replaceDatabase(String url, String databaseName) {
        int schemeEnd = url.indexOf("//");
        int databaseStart = url.indexOf('/', schemeEnd + 2);
        int queryStart = url.indexOf('?', databaseStart);
        String suffix = queryStart < 0 ? "" : url.substring(queryStart);
        return url.substring(0, databaseStart + 1) + databaseName + suffix;
    }

    interface DatabaseCallback {
        void accept(String url, String username, String password) throws Exception;
    }
}
