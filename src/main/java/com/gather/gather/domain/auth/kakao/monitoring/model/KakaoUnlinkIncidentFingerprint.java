package com.gather.gather.domain.auth.kakao.monitoring.model;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertType;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KakaoUnlinkIncidentFingerprint {

    public static final String SYNTHETIC_TEST_VALUE = "KAKAO_UNLINK:SYNTHETIC_TEST";
    private static final int MAX_LENGTH = 191;
    private static final Pattern DEAD_TASK_PATTERN =
            Pattern.compile("KAKAO_UNLINK:DEAD_TASK:([1-9][0-9]*):([0-9]+)");
    private static final Pattern STATE_INVARIANT_PATTERN =
            Pattern.compile("KAKAO_UNLINK:STATE_INVARIANT_VIOLATION:([A-Z_]+)");

    private final String value;
    private final KakaoUnlinkAlertType alertType;

    private KakaoUnlinkIncidentFingerprint(String value, KakaoUnlinkAlertType alertType) {
        if (value == null || alertType == null || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Kakao unlink incident fingerprint 값이 올바르지 않습니다.");
        }
        validateStored(value, alertType);
        this.value = value;
        this.alertType = alertType;
    }

    public static KakaoUnlinkIncidentFingerprint deadTask(long taskId, int retryCycle) {
        if (taskId <= 0 || retryCycle < 0) {
            throw new IllegalArgumentException("DEAD task fingerprint 값이 올바르지 않습니다.");
        }
        return new KakaoUnlinkIncidentFingerprint(
                "KAKAO_UNLINK:DEAD_TASK:" + taskId + ":" + retryCycle,
                KakaoUnlinkAlertType.DEAD_TASK);
    }

    public static KakaoUnlinkIncidentFingerprint singleton(KakaoUnlinkAlertType alertType) {
        if (alertType == null
                || alertType == KakaoUnlinkAlertType.DEAD_TASK
                || alertType == KakaoUnlinkAlertType.STATE_INVARIANT_VIOLATION
                || alertType == KakaoUnlinkAlertType.SYNTHETIC_TEST) {
            throw new IllegalArgumentException("singleton fingerprint alert type이 올바르지 않습니다.");
        }
        return new KakaoUnlinkIncidentFingerprint("KAKAO_UNLINK:" + alertType.name(), alertType);
    }

    public static KakaoUnlinkIncidentFingerprint stateInvariant(
            KakaoUnlinkStateInvariantType invariantType) {
        if (invariantType == null) {
            throw new IllegalArgumentException("state invariant fingerprint 종류는 필수입니다.");
        }
        return new KakaoUnlinkIncidentFingerprint(
                "KAKAO_UNLINK:STATE_INVARIANT_VIOLATION:" + invariantType.name(),
                KakaoUnlinkAlertType.STATE_INVARIANT_VIOLATION);
    }

    public static KakaoUnlinkIncidentFingerprint syntheticTest() {
        return new KakaoUnlinkIncidentFingerprint(
                SYNTHETIC_TEST_VALUE, KakaoUnlinkAlertType.SYNTHETIC_TEST);
    }

    public static void validateStored(String value, KakaoUnlinkAlertType alertType) {
        if (value == null || alertType == null || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Kakao unlink incident fingerprint 값이 올바르지 않습니다.");
        }
        switch (alertType) {
            case DEAD_TASK -> validateDeadTask(value);
            case STATE_INVARIANT_VIOLATION -> validateStateInvariant(value);
            case SYNTHETIC_TEST -> {
                if (!SYNTHETIC_TEST_VALUE.equals(value)) {
                    throw new IllegalArgumentException("synthetic fingerprint 값이 올바르지 않습니다.");
                }
            }
            case WORKER_CONFIGURATION_BLOCKED,
                    DEAD_TASK_SUMMARY,
                    OVERDUE_PENDING,
                    EXPIRED_PROCESSING,
                    WORKER_HEARTBEAT_MISSED,
                    BACKLOG_ACCUMULATION -> {
                if (!("KAKAO_UNLINK:" + alertType.name()).equals(value)) {
                    throw new IllegalArgumentException("singleton fingerprint 값이 올바르지 않습니다.");
                }
            }
        }
    }

    private static void validateDeadTask(String value) {
        Matcher matcher = DEAD_TASK_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("DEAD task fingerprint 형식이 올바르지 않습니다.");
        }
        try {
            Long.parseLong(matcher.group(1));
            Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("DEAD task fingerprint 범위가 올바르지 않습니다.", exception);
        }
    }

    private static void validateStateInvariant(String value) {
        Matcher matcher = STATE_INVARIANT_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("state invariant fingerprint 형식이 올바르지 않습니다.");
        }
        try {
            KakaoUnlinkStateInvariantType.valueOf(matcher.group(1));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "state invariant fingerprint 종류가 올바르지 않습니다.", exception);
        }
    }

    public String value() {
        return value;
    }

    public KakaoUnlinkAlertType alertType() {
        return alertType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof KakaoUnlinkIncidentFingerprint fingerprint)) {
            return false;
        }
        return value.equals(fingerprint.value) && alertType == fingerprint.alertType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, alertType);
    }

    @Override
    public String toString() {
        return "KakaoUnlinkIncidentFingerprint[alertType=" + alertType + "]";
    }
}
