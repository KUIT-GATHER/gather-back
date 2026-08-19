package com.gather.gather.domain.auth.kakao.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.kakao.monitoring.service.KakaoUnlinkIncidentReconciliationService;
import com.gather.gather.domain.auth.kakao.monitoring.service.KakaoUnlinkIncidentTransactionService;
import com.gather.gather.domain.auth.kakao.monitoring.service.KakaoUnlinkMonitorLeaseService;
import jakarta.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
class KakaoUnlinkMonitoringRuntimeInertTest {

    private static final String MONITORING_PACKAGE =
            "com.gather.gather.domain.auth.kakao.monitoring";
    private static final List<Class<?>> OUTBOUND_TYPES =
            List.of(
                    RestClient.class,
                    RestTemplate.class,
                    JavaMailSender.class,
                    HttpClient.class,
                    ApplicationEventPublisher.class);

    @Autowired private ApplicationContext applicationContext;

    @Autowired(required = false)
    private List<ScheduledTaskHolder> scheduledTaskHolders = List.of();

    @Test
    void foundationAddsNoAutomaticRunnerListenerSchedulerOrOutboundClient() {
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean = applicationContext.getBean(beanName);
            Class<?> type = AopUtils.getTargetClass(bean);
            if (!type.getPackageName().startsWith(MONITORING_PACKAGE)) {
                continue;
            }
            assertThat(ApplicationRunner.class.isAssignableFrom(type)).isFalse();
            assertThat(CommandLineRunner.class.isAssignableFrom(type)).isFalse();
            assertThat(Arrays.stream(type.getMethods())).noneMatch(this::isAutomaticEntryPoint);
            assertThat(allFields(type)).noneMatch(this::isOutboundDependency);
        }

        assertThat(
                        scheduledTaskHolders.stream()
                                .flatMap(holder -> holder.getScheduledTasks().stream())
                                .map(task -> task.getTask().getRunnable())
                                .filter(ScheduledMethodRunnable.class::isInstance)
                                .map(ScheduledMethodRunnable.class::cast)
                                .map(ScheduledMethodRunnable::getTarget)
                                .map(AopUtils::getTargetClass)
                                .map(Class::getPackageName))
                .noneMatch(packageName -> packageName.startsWith(MONITORING_PACKAGE));
    }

    @Test
    void transactionOwnersDeclareEveryExpectedRequiresNewBoundary() {
        Set<String> incidentMethods =
                requiresNewMethodNames(KakaoUnlinkIncidentTransactionService.class);
        assertThat(incidentMethods)
                .contains(
                        "observe",
                        "resolve",
                        "suppress",
                        "releaseSuppression",
                        "recordReminder",
                        "enqueueRecovered",
                        "enqueueSyntheticTest");

        Set<String> leaseMethods = requiresNewMethodNames(KakaoUnlinkMonitorLeaseService.class);
        assertThat(leaseMethods).containsExactlyInAnyOrder("tryAcquire", "complete", "fail");
        assertThat(KakaoUnlinkIncidentReconciliationService.class.getDeclaredMethods())
                .allMatch(method -> method.getAnnotation(Transactional.class) == null);
    }

    private Set<String> requiresNewMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .peek(
                        method ->
                                assertThat(method.getAnnotation(Transactional.class).propagation())
                                        .isEqualTo(Propagation.REQUIRES_NEW))
                .map(Method::getName)
                .collect(Collectors.toSet());
    }

    private boolean isAutomaticEntryPoint(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, Scheduled.class)
                || AnnotatedElementUtils.hasAnnotation(method, EventListener.class)
                || AnnotatedElementUtils.hasAnnotation(method, PostConstruct.class)
                || AnnotatedElementUtils.hasAnnotation(method, Async.class);
    }

    private List<Field> allFields(Class<?> type) {
        java.util.ArrayList<Field> fields = new java.util.ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            fields.addAll(List.of(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    private boolean isOutboundDependency(Field field) {
        return OUTBOUND_TYPES.stream()
                .anyMatch(outboundType -> outboundType.isAssignableFrom(field.getType()));
    }
}
