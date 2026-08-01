package com.gather.gather.domain.auth.kakao.worker;

import com.gather.gather.domain.auth.entity.KakaoUnlinkTask;
import com.gather.gather.domain.auth.entity.KakaoUnlinkWorkerControl;
import com.gather.gather.domain.auth.repository.KakaoUnlinkTaskRepository;
import com.gather.gather.domain.auth.repository.KakaoUnlinkWorkerControlRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KakaoUnlinkWorkerResumeService {

    private final KakaoUnlinkWorkerControlRepository controlRepository;
    private final KakaoUnlinkTaskRepository taskRepository;
    private final Clock clock;

    @Transactional
    public int resumeConfigurationTasks(
            List<Long> taskIds, String actor, KakaoUnlinkResumeReason reason) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new IllegalArgumentException("At least one task ID is required");
        }
        if (taskIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Task IDs must be positive");
        }
        if (reason == null) {
            throw new IllegalArgumentException("Actor and reason are required");
        }
        KakaoUnlinkResumeActor.normalize(actor);

        // Pessimistic locks are deterministic only when both requested and database IDs are sorted.
        List<Long> distinctIds = taskIds.stream().distinct().sorted().toList();
        KakaoUnlinkWorkerControl control =
                controlRepository
                        .findSingletonForUpdate()
                        .orElseThrow(
                                () ->
                                        new KakaoUnlinkResumeInvariantException(
                                                "Kakao unlink worker control is missing"));
        if (!control.isConfigurationBlocked()) {
            throw new KakaoUnlinkResumeInvariantException(
                    "Kakao unlink worker is not configuration-blocked");
        }

        List<KakaoUnlinkTask> tasks = taskRepository.findAllConfigurationDeadForUpdate();
        List<Long> databaseTaskIds = tasks.stream().map(KakaoUnlinkTask::getId).toList();
        // Resume is all-or-nothing: the request must match every DEAD/CONFIGURATION task.
        if (!databaseTaskIds.equals(distinctIds)) {
            throw new KakaoUnlinkResumeInvariantException(
                    "Requested task IDs must match every CONFIGURATION DEAD task");
        }

        LocalDateTime resumeNow = LocalDateTime.now(clock);
        tasks.forEach(task -> task.startNewRetryCycle(resumeNow));
        control.resume(resumeNow);
        return tasks.size();
    }
}
