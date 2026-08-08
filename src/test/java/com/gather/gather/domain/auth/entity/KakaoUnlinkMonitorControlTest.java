package com.gather.gather.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class KakaoUnlinkMonitorControlTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 8, 0, 0);

    @Test
    void staleLeaseCannotClearNewerLease() {
        KakaoUnlinkMonitorControl control = control();
        long firstSequence = control.acquire("token-1", "owner-1", NOW, NOW.plusMinutes(1));
        long secondSequence =
                control.acquire("token-2", "owner-2", NOW.plusMinutes(2), NOW.plusMinutes(5));

        assertThat(
                        control.complete(
                                firstSequence,
                                "owner-1",
                                "token-1",
                                NOW.plusMinutes(2).plusSeconds(1)))
                .isFalse();
        assertThat(control.getLeaseToken()).isEqualTo("token-2");
        assertThat(control.complete(secondSequence, "owner-2", "token-2", NOW.plusMinutes(3)))
                .isTrue();
        assertThat(control.getLeaseToken()).isNull();
    }

    private KakaoUnlinkMonitorControl control() {
        KakaoUnlinkMonitorControl control = new KakaoUnlinkMonitorControl();
        ReflectionTestUtils.setField(control, "id", 1L);
        ReflectionTestUtils.setField(control, "scanSequence", 0L);
        ReflectionTestUtils.setField(control, "updatedAt", NOW);
        ReflectionTestUtils.setField(control, "version", 0L);
        return control;
    }
}
