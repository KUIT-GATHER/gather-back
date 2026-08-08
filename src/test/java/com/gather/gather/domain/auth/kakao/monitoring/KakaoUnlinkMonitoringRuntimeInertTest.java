package com.gather.gather.domain.auth.kakao.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.kakao.monitoring.service.KakaoUnlinkAlertDeliveryPersistenceService;
import com.gather.gather.domain.auth.kakao.monitoring.service.KakaoUnlinkIncidentReconciliationService;
import com.gather.gather.domain.auth.kakao.monitoring.service.KakaoUnlinkIncidentTransactionService;
import com.gather.gather.domain.auth.kakao.monitoring.service.KakaoUnlinkMonitorLeaseService;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class KakaoUnlinkMonitoringRuntimeInertTest {

    @Test
    void foundationAddsNoAutomaticRunnerOrScheduledMethod() {
        List<Class<?>> foundationTypes =
                List.of(
                        KakaoUnlinkMonitorLeaseService.class,
                        KakaoUnlinkIncidentReconciliationService.class,
                        KakaoUnlinkIncidentTransactionService.class,
                        KakaoUnlinkAlertDeliveryPersistenceService.class);

        for (Class<?> foundationType : foundationTypes) {
            assertThat(ApplicationRunner.class.isAssignableFrom(foundationType)).isFalse();
            assertThat(CommandLineRunner.class.isAssignableFrom(foundationType)).isFalse();
            assertThat(List.of(foundationType.getDeclaredMethods())).noneMatch(this::isScheduled);
            assertThat(List.of(foundationType.getDeclaredFields()))
                    .noneMatch(
                            field -> {
                                String typeName = field.getType().getName();
                                return typeName.contains("RestClient")
                                        || typeName.contains("JavaMailSender");
                            });
        }
    }

    @Test
    void incidentTransactionOwnerUsesRequiresNewWithoutFacadeSelfInvocation() {
        List<String> transactionMethods =
                List.of(
                        "observe",
                        "resolve",
                        "suppress",
                        "releaseSuppression",
                        "recordReminder",
                        "enqueueRecovered",
                        "enqueueSyntheticTest");

        for (Method method : KakaoUnlinkIncidentTransactionService.class.getDeclaredMethods()) {
            if (transactionMethods.contains(method.getName())) {
                Transactional transactional = method.getAnnotation(Transactional.class);
                assertThat(transactional).isNotNull();
                assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
            }
        }
        assertThat(KakaoUnlinkIncidentReconciliationService.class.getDeclaredMethods())
                .allMatch(method -> method.getAnnotation(Transactional.class) == null);
    }

    private boolean isScheduled(Method method) {
        return method.isAnnotationPresent(Scheduled.class);
    }
}
