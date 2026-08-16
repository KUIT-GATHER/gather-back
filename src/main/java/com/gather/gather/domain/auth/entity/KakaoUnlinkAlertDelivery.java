package com.gather.gather.domain.auth.entity;

import com.gather.gather.domain.auth.kakao.monitoring.model.OperationalAlertPayloadSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "kakao_unlink_alert_delivery")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KakaoUnlinkAlertDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private KakaoUnlinkIncident incident;

    @Column(nullable = false)
    private int occurrenceNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KakaoUnlinkAlertEventType eventType;

    @Column(nullable = false)
    private int eventSequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KakaoUnlinkAlertChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KakaoUnlinkAlertDeliveryStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json", nullable = false)
    private OperationalAlertPayloadSnapshot payloadSnapshot;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private LocalDateTime availableAt;

    @Column(length = 64)
    private String claimToken;

    @Column(length = 128)
    private String claimedBy;

    @Column private LocalDateTime claimedAt;

    @Column private LocalDateTime leaseExpiresAt;

    @Column private LocalDateTime sentAt;

    @Column(length = 80)
    private String lastFailureType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
