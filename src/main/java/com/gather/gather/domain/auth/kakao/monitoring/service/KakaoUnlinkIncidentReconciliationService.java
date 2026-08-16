package com.gather.gather.domain.auth.kakao.monitoring.service;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertChannel;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkAlertDeliveryResult;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentObservation;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentResolution;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentSnapshot;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentSuppression;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkObservationResult;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkRecoveredDeliveryRequest;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkRecoveredDeliveryResult;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkReminderRequest;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkSuppressionRelease;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KakaoUnlinkIncidentReconciliationService {

    private final KakaoUnlinkIncidentTransactionService transactionService;

    public KakaoUnlinkObservationResult observe(KakaoUnlinkIncidentObservation observation) {
        return transactionService.observe(observation);
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

    public KakaoUnlinkRecoveredDeliveryResult enqueueRecovered(
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
}
