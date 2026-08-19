ALTER TABLE kakao_unlink_worker_control
    ADD COLUMN last_poll_started_at DATETIME(6) NULL,
    ADD COLUMN last_poll_completed_at DATETIME(6) NULL,
    ADD COLUMN last_poll_failed_at DATETIME(6) NULL,
    ADD COLUMN last_poll_failure_type VARCHAR(80) NULL;

CREATE TABLE kakao_unlink_monitor_control (
    id BIGINT NOT NULL,
    scan_sequence BIGINT NOT NULL DEFAULT 0,
    lease_token VARCHAR(64) NULL,
    lease_owner VARCHAR(128) NULL,
    lease_acquired_at DATETIME(6) NULL,
    lease_expires_at DATETIME(6) NULL,
    last_scan_started_at DATETIME(6) NULL,
    last_scan_completed_at DATETIME(6) NULL,
    last_scan_failed_at DATETIME(6) NULL,
    last_scan_failure_type VARCHAR(80) NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT chk_kakao_unlink_monitor_control_singleton
        CHECK (id = 1),
    CONSTRAINT chk_kakao_unlink_monitor_control_scan_sequence
        CHECK (scan_sequence >= 0),
    CONSTRAINT chk_kakao_unlink_monitor_control_lease_fields
        CHECK (
            (
                lease_token IS NULL
                AND lease_owner IS NULL
                AND lease_acquired_at IS NULL
                AND lease_expires_at IS NULL
            )
            OR
            (
                lease_token IS NOT NULL
                AND lease_owner IS NOT NULL
                AND lease_acquired_at IS NOT NULL
                AND lease_expires_at IS NOT NULL
                AND lease_expires_at > lease_acquired_at
            )
        )
);

INSERT INTO kakao_unlink_monitor_control (
    id,
    scan_sequence,
    updated_at,
    version
) VALUES (
    1,
    0,
    UTC_TIMESTAMP(6),
    0
);

CREATE TABLE kakao_unlink_incident (
    id BIGINT NOT NULL AUTO_INCREMENT,
    fingerprint VARCHAR(191)
        CHARACTER SET ascii
        COLLATE ascii_bin
        NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    occurrence_no INT NOT NULL DEFAULT 1,
    severity_escalation_no INT NOT NULL DEFAULT 0,
    opened_at DATETIME(6) NOT NULL,
    last_observed_at DATETIME(6) NOT NULL,
    last_observed_scan_sequence BIGINT NOT NULL DEFAULT 0,
    resolved_at DATETIME(6) NULL,
    notification_state VARCHAR(20) NOT NULL DEFAULT 'ELIGIBLE',
    suppressed_by_incident_id BIGINT NULL,
    suppressed_by_occurrence_no INT NULL,
    suppressed_at DATETIME(6) NULL,
    notification_eligible_at DATETIME(6) NULL,
    next_discord_reminder_at DATETIME(6) NULL,
    next_email_reminder_at DATETIME(6) NULL,
    safe_details JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_kakao_unlink_incident_fingerprint
        UNIQUE (fingerprint),
    CONSTRAINT fk_kakao_unlink_incident_suppressed_by
        FOREIGN KEY (suppressed_by_incident_id)
        REFERENCES kakao_unlink_incident (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    CONSTRAINT chk_kakao_unlink_incident_occurrence
        CHECK (occurrence_no >= 1),
    CONSTRAINT chk_kakao_unlink_incident_escalation
        CHECK (severity_escalation_no >= 0),
    CONSTRAINT chk_kakao_unlink_incident_scan_sequence
        CHECK (last_observed_scan_sequence >= 0),
    CONSTRAINT chk_kakao_unlink_incident_status
        CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT chk_kakao_unlink_incident_severity
        CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_kakao_unlink_incident_notification_state
        CHECK (notification_state IN ('ELIGIBLE', 'SUPPRESSED')),
    CONSTRAINT chk_kakao_unlink_incident_resolution
        CHECK (
            (status = 'OPEN' AND resolved_at IS NULL)
            OR
            (status = 'RESOLVED' AND resolved_at IS NOT NULL)
        ),
    CONSTRAINT chk_kakao_unlink_incident_observed_time
        CHECK (last_observed_at >= opened_at),
    CONSTRAINT chk_kakao_unlink_incident_resolved_time
        CHECK (resolved_at IS NULL OR resolved_at >= opened_at),
    CONSTRAINT chk_kakao_unlink_incident_suppression
        CHECK (
            (
                notification_state = 'ELIGIBLE'
                AND suppressed_by_incident_id IS NULL
                AND suppressed_by_occurrence_no IS NULL
                AND suppressed_at IS NULL
            )
            OR
            (
                notification_state = 'SUPPRESSED'
                AND suppressed_by_incident_id IS NOT NULL
                AND suppressed_by_occurrence_no IS NOT NULL
                AND suppressed_by_occurrence_no >= 1
                AND suppressed_at IS NOT NULL
            )
        ),
    CONSTRAINT chk_kakao_unlink_incident_safe_details
        CHECK (JSON_TYPE(safe_details) = 'OBJECT'),
    INDEX idx_kakao_unlink_incident_open_type
        (status, alert_type, id),
    INDEX idx_kakao_unlink_incident_discord_reminder
        (status, notification_state, next_discord_reminder_at, id),
    INDEX idx_kakao_unlink_incident_email_reminder
        (status, notification_state, next_email_reminder_at, id),
    INDEX idx_kakao_unlink_incident_suppressed_by
        (suppressed_by_incident_id)
);

CREATE TABLE kakao_unlink_alert_delivery (
    id BIGINT NOT NULL AUTO_INCREMENT,
    incident_id BIGINT NOT NULL,
    occurrence_no INT NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    event_sequence INT NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payload_snapshot JSON NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL,
    claim_token VARCHAR(64) NULL,
    claimed_by VARCHAR(128) NULL,
    claimed_at DATETIME(6) NULL,
    lease_expires_at DATETIME(6) NULL,
    sent_at DATETIME(6) NULL,
    last_failure_type VARCHAR(80) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_kakao_unlink_alert_delivery_event
        UNIQUE (
            incident_id,
            occurrence_no,
            event_type,
            event_sequence,
            channel
        ),
    CONSTRAINT fk_kakao_unlink_alert_delivery_incident
        FOREIGN KEY (incident_id)
        REFERENCES kakao_unlink_incident (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    CONSTRAINT chk_kakao_unlink_alert_delivery_occurrence
        CHECK (occurrence_no >= 1),
    CONSTRAINT chk_kakao_unlink_alert_delivery_sequence
        CHECK (event_sequence >= 1),
    CONSTRAINT chk_kakao_unlink_alert_delivery_sequence_by_event
        CHECK (
            (
                event_type IN ('INITIAL', 'RECOVERED')
                AND event_sequence = 1
            )
            OR event_type IN ('REMINDER', 'ESCALATED', 'TEST')
        ),
    CONSTRAINT chk_kakao_unlink_alert_delivery_channel
        CHECK (channel IN ('DISCORD', 'EMAIL')),
    CONSTRAINT chk_kakao_unlink_alert_delivery_status
        CHECK (
            status IN (
                'PENDING',
                'PROCESSING',
                'SUCCEEDED',
                'FAILED',
                'CANCELLED'
            )
        ),
    CONSTRAINT chk_kakao_unlink_alert_delivery_attempt
        CHECK (attempt_count >= 0),
    CONSTRAINT chk_kakao_unlink_alert_delivery_claim
        CHECK (
            (
                status = 'PROCESSING'
                AND claim_token IS NOT NULL
                AND claimed_by IS NOT NULL
                AND claimed_at IS NOT NULL
                AND lease_expires_at IS NOT NULL
                AND lease_expires_at > claimed_at
            )
            OR
            (
                status <> 'PROCESSING'
                AND claim_token IS NULL
                AND claimed_by IS NULL
                AND claimed_at IS NULL
                AND lease_expires_at IS NULL
            )
        ),
    CONSTRAINT chk_kakao_unlink_alert_delivery_sent
        CHECK (
            (status = 'SUCCEEDED' AND sent_at IS NOT NULL)
            OR
            (status <> 'SUCCEEDED' AND sent_at IS NULL)
        ),
    CONSTRAINT chk_kakao_unlink_alert_delivery_payload
        CHECK (JSON_TYPE(payload_snapshot) = 'OBJECT'),
    INDEX idx_kakao_unlink_alert_delivery_due
        (status, available_at, id),
    INDEX idx_kakao_unlink_alert_delivery_lease
        (status, lease_expires_at, id),
    INDEX idx_kakao_unlink_alert_delivery_success
        (incident_id, occurrence_no, channel, status, event_type)
);

INSERT INTO kakao_unlink_incident (
    fingerprint,
    alert_type,
    severity,
    status,
    occurrence_no,
    severity_escalation_no,
    opened_at,
    last_observed_at,
    last_observed_scan_sequence,
    resolved_at,
    notification_state,
    safe_details,
    created_at,
    updated_at,
    version
) VALUES (
    'KAKAO_UNLINK:SYNTHETIC_TEST',
    'SYNTHETIC_TEST',
    'INFO',
    'OPEN',
    1,
    0,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    0,
    NULL,
    'ELIGIBLE',
    JSON_OBJECT('kind', 'SYNTHETIC_TEST'),
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    0
);
