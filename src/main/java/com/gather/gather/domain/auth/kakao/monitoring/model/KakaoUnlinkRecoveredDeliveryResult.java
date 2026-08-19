package com.gather.gather.domain.auth.kakao.monitoring.model;

public record KakaoUnlinkRecoveredDeliveryResult(
        Outcome outcome, KakaoUnlinkAlertDeliveryResult delivery) {

    public enum Outcome {
        ENQUEUED,
        ALREADY_ENQUEUED,
        NOT_APPLICABLE,
        NO_SUCCESSFUL_PROBLEM_ALERT
    }

    public KakaoUnlinkRecoveredDeliveryResult {
        if (outcome == null) {
            throw new IllegalArgumentException("RECOVERED delivery 결과는 필수입니다.");
        }
        boolean deliveryRequired =
                outcome == Outcome.ENQUEUED || outcome == Outcome.ALREADY_ENQUEUED;
        if (deliveryRequired != (delivery != null)) {
            throw new IllegalArgumentException("RECOVERED delivery 결과 조합이 올바르지 않습니다.");
        }
    }

    public static KakaoUnlinkRecoveredDeliveryResult enqueued(
            KakaoUnlinkAlertDeliveryResult delivery) {
        return new KakaoUnlinkRecoveredDeliveryResult(
                delivery.created() ? Outcome.ENQUEUED : Outcome.ALREADY_ENQUEUED, delivery);
    }

    public static KakaoUnlinkRecoveredDeliveryResult notApplicable() {
        return new KakaoUnlinkRecoveredDeliveryResult(Outcome.NOT_APPLICABLE, null);
    }

    public static KakaoUnlinkRecoveredDeliveryResult noSuccessfulProblemAlert() {
        return new KakaoUnlinkRecoveredDeliveryResult(Outcome.NO_SUCCESSFUL_PROBLEM_ALERT, null);
    }
}
