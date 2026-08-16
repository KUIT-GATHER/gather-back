package com.gather.gather.domain.auth.kakao.monitoring.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.gather.domain.auth.kakao.monitoring.exception.KakaoUnlinkMonitoringInvariantException;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentSafeDetails;
import com.gather.gather.domain.auth.kakao.monitoring.model.OperationalAlertPayloadSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakaoUnlinkMonitoringJsonCodec {

    private final ObjectMapper objectMapper;

    public String write(KakaoUnlinkIncidentSafeDetails safeValue) {
        return writeTyped(safeValue, KakaoUnlinkIncidentSafeDetails.class);
    }

    public String write(OperationalAlertPayloadSnapshot safeValue) {
        return writeTyped(safeValue, OperationalAlertPayloadSnapshot.class);
    }

    private String writeTyped(Object safeValue, Class<?> declaredType) {
        if (safeValue == null) {
            throw new IllegalArgumentException("직렬화할 typed monitoring 값은 필수입니다.");
        }
        try {
            return objectMapper.writerFor(declaredType).writeValueAsString(safeValue);
        } catch (JsonProcessingException exception) {
            throw new KakaoUnlinkMonitoringInvariantException(
                    "typed monitoring JSON 직렬화에 실패했습니다.", exception);
        }
    }
}
