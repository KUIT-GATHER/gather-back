package com.gather.gather.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
class ProfileImageDeletionListenerTest {

    private static final Long UPLOAD_ID = 99L;

    @Mock private ProfileImageCleanupService profileImageCleanupService;

    @Test
    @DisplayName("기존 이미지 삭제는 트랜잭션 커밋 이후 단계로 등록되어 있다")
    void deletePreviousImage_isRegisteredAfterCommit() throws NoSuchMethodException {
        Method method =
                ProfileImageDeletionListener.class.getDeclaredMethod(
                        "deletePreviousImage", ProfileImageReplacedEvent.class);

        TransactionalEventListener annotation =
                method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    @DisplayName("커밋 후 이벤트에서 기존 S3 객체 삭제를 요청한다")
    void deletePreviousImage_deletesPreviousObject() {
        ProfileImageDeletionListener listener =
                new ProfileImageDeletionListener(profileImageCleanupService);

        listener.deletePreviousImage(new ProfileImageReplacedEvent(UPLOAD_ID));

        verify(profileImageCleanupService).deletePreviousObject(UPLOAD_ID);
    }

    @Test
    @DisplayName("기존 S3 객체 삭제가 실패해도 예외를 전파하지 않는다")
    void deletePreviousImage_swallowsStorageFailure() {
        ProfileImageDeletionListener listener =
                new ProfileImageDeletionListener(profileImageCleanupService);
        doThrow(new IllegalStateException("failed"))
                .when(profileImageCleanupService)
                .deletePreviousObject(UPLOAD_ID);

        assertThatCode(() -> listener.deletePreviousImage(new ProfileImageReplacedEvent(UPLOAD_ID)))
                .doesNotThrowAnyException();
    }

    @Test
    void deleteWithdrawnProfileImage_delegatesToSameRetryableCleanup() {
        ProfileImageDeletionListener listener =
                new ProfileImageDeletionListener(profileImageCleanupService);

        listener.deleteWithdrawnProfileImage(new ProfileImageDeletionRequestedEvent(UPLOAD_ID));

        verify(profileImageCleanupService).deletePreviousObject(UPLOAD_ID);
    }
}
