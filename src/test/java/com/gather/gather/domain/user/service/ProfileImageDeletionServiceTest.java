package com.gather.gather.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gather.gather.domain.user.entity.ProfileImageUpload;
import com.gather.gather.domain.user.repository.ProfileImageUploadRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProfileImageDeletionServiceTest {

    @Mock private ProfileImageUploadRepository profileImageUploadRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ProfileImageDeletionService service;

    @BeforeEach
    void setUp() {
        service = new ProfileImageDeletionService(profileImageUploadRepository, eventPublisher);
        lenient()
                .when(profileImageUploadRepository.save(any(ProfileImageUpload.class)))
                .thenAnswer(
                        invocation -> {
                            ProfileImageUpload upload = invocation.getArgument(0);
                            ReflectionTestUtils.setField(upload, "id", 10L);
                            return upload;
                        });
    }

    @Test
    void scheduleDeletion_persistsDurableTaskAndPublishesAccelerationEvent() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 14, 25, 56, 123_456_000);

        service.scheduleDeletion(1L, "profiles/1/current.jpg", now);

        ArgumentCaptor<ProfileImageUpload> taskCaptor =
                ArgumentCaptor.forClass(ProfileImageUpload.class);
        verify(profileImageUploadRepository).save(taskCaptor.capture());
        ProfileImageUpload task = taskCaptor.getValue();
        assertThat(task.getObjectKey()).startsWith("__PROFILE_IMAGE_DELETION_TASK__/1/");
        assertThat(task.getPreviousObjectKey()).isEqualTo("profiles/1/current.jpg");
        assertThat(task.getCreatedAt()).isEqualTo(now);
        assertThat(task.getAppliedAt()).isEqualTo(now);
        verify(eventPublisher).publishEvent(new ProfileImageDeletionRequestedEvent(10L));
    }

    @Test
    void scheduleDeletion_usesDistinctTrackingKeysForDatabaseUniqueDefense() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 14, 25, 56);

        service.scheduleDeletion(1L, "profiles/1/current.jpg", now);
        service.scheduleDeletion(1L, "profiles/1/current.jpg", now);

        ArgumentCaptor<ProfileImageUpload> taskCaptor =
                ArgumentCaptor.forClass(ProfileImageUpload.class);
        verify(profileImageUploadRepository, times(2)).save(taskCaptor.capture());
        assertThat(taskCaptor.getAllValues().get(0).getObjectKey())
                .isNotEqualTo(taskCaptor.getAllValues().get(1).getObjectKey());
    }

    @Test
    void scheduleDeletion_doesNothingWhenProfileImageKeyIsNull() {
        service.scheduleDeletion(1L, null, LocalDateTime.of(2026, 7, 31, 14, 25, 56));

        verify(profileImageUploadRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void scheduleDeletion_propagatesPersistenceFailureWithoutPublishingEvent() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 14, 25, 56);
        lenient()
                .when(profileImageUploadRepository.save(any(ProfileImageUpload.class)))
                .thenThrow(new IllegalStateException("save failed"));

        assertThatThrownBy(() -> service.scheduleDeletion(1L, "profiles/1/current.jpg", now))
                .isInstanceOf(IllegalStateException.class);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
