package com.gather.gather.domain.auth.repository;

import static com.gather.gather.domain.auth.repository.EmailVerificationCodeHashMigrationIntegrationTest.insertCurrentRow;
import static com.gather.gather.domain.auth.repository.EmailVerificationCodeHashMigrationIntegrationTest.insertLegacyRow;
import static com.gather.gather.domain.auth.repository.EmailVerificationCodeHashMigrationIntegrationTest.queryRow;
import static com.gather.gather.domain.auth.repository.EmailVerificationCodeHashMigrationIntegrationTest.withUpgradeDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.gather.gather.domain.auth.dto.EmailVerificationConfirmRequest;
import com.gather.rollback.legacy.LegacyEmailVerification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/**
 * V65가 적용된 스키마에서 롤백된 구 버전 JAR이 계속 동작할 수 있는지 검증한다.
 *
 * <p>배포 중 헬스체크가 실패하면 JAR만 되돌아가고 마이그레이션은 되돌아가지 않으므로, 확장 전용 설계가 실제로 구 버전을 살려두는지 확인해야 한다.
 */
@SpringBootTest
class EmailVerificationRollbackCompatibilityIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 12, 0);

    @Autowired private DataSourceProperties dataSourceProperties;

    @Test
    @DisplayName("구 버전 매핑은 확장된 스키마에서 Hibernate 스키마 검증을 통과한다")
    void legacyMapping_passesHibernateSchemaValidation() throws Exception {
        withMigratedDatabase(
                (url, username, password) ->
                        assertThatCode(
                                        () -> {
                                            EntityManagerFactory factory =
                                                    legacyEntityManagerFactory(
                                                            url, username, password);
                                            factory.close();
                                        })
                                .doesNotThrowAnyException());
    }

    @Test
    @DisplayName("구 버전은 code_hash 없이 새 인증 행을 저장할 수 있다")
    void legacyMapping_insertsRowWithoutCodeHash() throws Exception {
        withMigratedDatabase(
                (url, username, password) -> {
                    String email = "legacy-insert@example.com";
                    withLegacyEntityManager(
                            url,
                            username,
                            password,
                            entityManager -> {
                                entityManager.getTransaction().begin();
                                entityManager.persist(
                                        new LegacyEmailVerification(
                                                email,
                                                UUID.randomUUID().toString(),
                                                "123456",
                                                NOW.plusMinutes(10),
                                                NOW));
                                entityManager.getTransaction().commit();
                            });

                    Map<String, Object> row = row(url, username, password, email);
                    assertThat(row.get("code")).isEqualTo("123456");
                    assertThat(row.get("code_hash")).isNull();
                });
    }

    @Test
    @DisplayName("구 버전 재발송은 code만 갱신하고 이전 해시를 낡은 값으로 남긴다")
    void legacyMapping_resendLeavesStaleCodeHash() throws Exception {
        withMigratedDatabase(
                (url, username, password) -> {
                    String email = "legacy-resend@example.com";
                    insertCurrentRow(url, username, password, email, "b".repeat(64));

                    withLegacyEntityManager(
                            url,
                            username,
                            password,
                            entityManager -> {
                                entityManager.getTransaction().begin();
                                LegacyEmailVerification stored =
                                        entityManager
                                                .createQuery(
                                                        "select v from LegacyEmailVerification v"
                                                                + " where v.email = :email",
                                                        LegacyEmailVerification.class)
                                                .setParameter("email", email)
                                                .getSingleResult();
                                stored.refresh(
                                        UUID.randomUUID().toString(), "654321", NOW.plusMinutes(5));
                                entityManager.getTransaction().commit();
                            });

                    Map<String, Object> row = row(url, username, password, email);
                    assertThat(row.get("code")).isEqualTo("654321");
                    // 구 버전은 code_hash를 모르므로 이전 값이 그대로 남아 낡은 해시가 된다.
                    assertThat(row.get("code_hash")).isEqualTo("b".repeat(64));
                });
    }

    @Test
    @DisplayName("구 버전은 새 애플리케이션이 만든 HMAC 행을 어떤 입력으로도 인증하지 못한다")
    void legacyMapping_cannotConfirmCurrentHmacRow() throws Exception {
        withMigratedDatabase(
                (url, username, password) -> {
                    String email = "legacy-confirm@example.com";
                    insertCurrentRow(url, username, password, email, "c".repeat(64));

                    withLegacyEntityManager(
                            url,
                            username,
                            password,
                            entityManager -> {
                                LegacyEmailVerification stored =
                                        entityManager
                                                .createQuery(
                                                        "select v from LegacyEmailVerification v"
                                                                + " where v.email = :email",
                                                        LegacyEmailVerification.class)
                                                .setParameter("email", email)
                                                .getSingleResult();

                                assertThat(stored.getCode()).isEmpty();
                                assertThat(stored.matchesCode("123456")).isFalse();
                                assertThat(stored.matchesCode("000000")).isFalse();
                                assertThat(stored.matchesCode(" ")).isFalse();
                            });
                });
    }

    @Test
    @DisplayName("빈 코드는 요청 검증에서 막히므로 구 버전 비교가 성립할 입력 자체가 없다")
    void blankCode_isRejectedByRequestValidation() {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();

            assertThat(
                            validator.validate(
                                    new EmailVerificationConfirmRequest("user@example.com", "")))
                    .isNotEmpty();
            assertThat(
                            validator.validate(
                                    new EmailVerificationConfirmRequest("user@example.com", "   ")))
                    .isNotEmpty();
            assertThat(
                            validator.validate(
                                    new EmailVerificationConfirmRequest("user@example.com", null)))
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("구 버전이 남긴 평문 행은 새 애플리케이션의 파기 조건에 걸린다")
    void legacyRows_matchCurrentPurgeCondition() throws Exception {
        withMigratedDatabase(
                (url, username, password) -> {
                    insertLegacyRow(url, username, password, "purge-plaintext@example.com", "1234");
                    insertCurrentRow(
                            url, username, password, "purge-current@example.com", "d".repeat(64));

                    Map<String, Object> legacyCount =
                            queryRow(
                                    url,
                                    username,
                                    password,
                                    "SELECT COUNT(*) AS legacy_count FROM email_verification"
                                            + " WHERE code <> '' OR code_hash IS NULL");
                    assertThat(((Number) legacyCount.get("legacy_count")).intValue()).isEqualTo(1);
                });
    }

    private Map<String, Object> row(String url, String username, String password, String email)
            throws Exception {
        return queryRow(
                url,
                username,
                password,
                "SELECT code, code_hash FROM email_verification WHERE email = '" + email + "'");
    }

    private void withMigratedDatabase(
            EmailVerificationCodeHashMigrationIntegrationTest.DatabaseCallback callback)
            throws Exception {
        withUpgradeDatabase(dataSourceProperties, "gather_evr_", null, callback);
    }

    private void withLegacyEntityManager(
            String url, String username, String password, Consumer<EntityManager> action) {
        EntityManagerFactory factory = legacyEntityManagerFactory(url, username, password);
        try (EntityManager entityManager = factory.createEntityManager()) {
            action.accept(entityManager);
        } finally {
            factory.close();
        }
    }

    /** 구 버전 매핑만 등록한 EntityManagerFactory. {@code validate}이므로 스키마가 어긋나면 생성 단계에서 실패한다. */
    private EntityManagerFactory legacyEntityManagerFactory(
            String url, String username, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
        dataSource.setDriverClassName(dataSourceProperties.determineDriverClassName());

        LocalContainerEntityManagerFactoryBean factoryBean =
                new LocalContainerEntityManagerFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setPackagesToScan(LegacyEmailVerification.class.getPackageName());
        factoryBean.setPersistenceUnitName("legacy-email-verification");
        factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Properties jpaProperties = new Properties();
        jpaProperties.setProperty("hibernate.hbm2ddl.auto", "validate");
        factoryBean.setJpaProperties(jpaProperties);

        factoryBean.afterPropertiesSet();
        return factoryBean.getObject();
    }
}
