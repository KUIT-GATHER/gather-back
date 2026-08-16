package com.gather.gather.domain.auth.kakao.monitoring.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertType;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = DeadTaskSafeDetails.class, name = "DEAD_TASK"),
    @JsonSubTypes.Type(value = DeadTaskSummarySafeDetails.class, name = "DEAD_TASK_SUMMARY"),
    @JsonSubTypes.Type(value = TaskPopulationSafeDetails.class, name = "TASK_POPULATION"),
    @JsonSubTypes.Type(value = WorkerControlSafeDetails.class, name = "WORKER_CONTROL"),
    @JsonSubTypes.Type(value = StateInvariantSafeDetails.class, name = "STATE_INVARIANT"),
    @JsonSubTypes.Type(value = SyntheticTestSafeDetails.class, name = "SYNTHETIC_TEST")
})
public sealed interface KakaoUnlinkIncidentSafeDetails
        permits DeadTaskSafeDetails,
                DeadTaskSummarySafeDetails,
                TaskPopulationSafeDetails,
                WorkerControlSafeDetails,
                StateInvariantSafeDetails,
                SyntheticTestSafeDetails {

    int MAX_SAMPLES = 5;

    boolean supports(KakaoUnlinkAlertType alertType);
}
