package com.gather.gather.domain.auth.kakao.monitoring.service;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertChannel;
import com.gather.gather.domain.auth.kakao.monitoring.exception.KakaoUnlinkMonitoringInvariantException;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkAlertDeliveryResult;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentObservation;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentResolution;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentSnapshot;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentSuppression;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkRecoveredDeliveryRequest;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkReminderRequest;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkSuppressionRelease;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KakaoUnlinkIncidentReconciliationService {

    private static final int MYSQL_DUPLICATE_KEY = 1062;
    private final KakaoUnlinkIncidentTransactionService transactionService;

    public KakaoUnlinkIncidentSnapshot observe(KakaoUnlinkIncidentObservation observation) {
        try {
            return transactionService.observe(observation);
        } catch (DataIntegrityViolationException exception) {
            if (!isDuplicateKey(exception)) {
                throw exception;
            }
            try {
                return transactionService.observe(observation);
            } catch (DataIntegrityViolationException retryException) {
                if (isDuplicateKey(retryException)) {
                    throw new KakaoUnlinkMonitoringInvariantException(
                            "incident/delivery UNIQUE 경합이 재시도 후에도 수렴하지 않았습니다.", retryException);
                }
                throw retryException;
            }
        }
    }

    public KakaoUnlinkIncidentSnapshot resolve(KakaoUnlinkIncidentResolution resolution) {
        return transactionService.resolve(resolution);
    }

    public KakaoUnlinkIncidentSnapshot suppress(KakaoUnlinkIncidentSuppression suppression) {
        return transactionService.suppress(suppression);
    }

    public KakaoUnlinkIncidentSnapshot releaseSuppression(KakaoUnlinkSuppressionRelease release) {
        return transactionService.releaseSuppression(release);
    }

    public KakaoUnlinkAlertDeliveryResult recordReminder(KakaoUnlinkReminderRequest request) {
        return transactionService.recordReminder(request);
    }

    public KakaoUnlinkAlertDeliveryResult enqueueRecovered(
            KakaoUnlinkRecoveredDeliveryRequest request) {
        return transactionService.enqueueRecovered(request);
    }

    public List<KakaoUnlinkAlertDeliveryResult> enqueueSyntheticTest(
            Set<KakaoUnlinkAlertChannel> channels) {
        return transactionService.enqueueSyntheticTest(channels);
    }

    public boolean hasSuccessfulProblemDelivery(
            long incidentId, int occurrenceNo, KakaoUnlinkAlertChannel channel) {
        return transactionService.hasSuccessfulProblemDelivery(incidentId, occurrenceNo, channel);
    }

    private boolean isDuplicateKey(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && sqlException.getErrorCode() == MYSQL_DUPLICATE_KEY) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
