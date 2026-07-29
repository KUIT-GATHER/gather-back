package com.gather.gather.domain.meeting.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.posting.entity.PostingCategory;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MeetingTest {

    @Test
    @DisplayName("활동 종료 시간이 없는 자유 모임은 활동 종료 상태가 아니다")
    void isActivityEnded_returnsFalse_whenActivityEndAtIsNull() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 12, 0);

        Meeting meeting =
                Meeting.create(
                        "자유 모임",
                        "아직 활동 일정이 정해지지 않은 모임",
                        10,
                        now.plusDays(7),
                        null,
                        Set.of(PostingCategory.ENVIRONMENT),
                        1L,
                        mock(User.class),
                        null,
                        null,
                        null,
                        null);

        assertThat(meeting.isActivityEnded(now)).isFalse();
    }

    @Test
    @DisplayName("활동 종료 시간이 현재보다 이전이면 활동 종료 상태다")
    void isActivityEnded_returnsTrue_whenActivityEndAtIsBeforeNow() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 12, 0);

        Meeting meeting =
                Meeting.create(
                        "공고 기반 모임",
                        "활동 일정이 정해진 모임",
                        10,
                        now.minusDays(2),
                        null,
                        Set.of(PostingCategory.WELFARE),
                        1L,
                        mock(User.class),
                        null,
                        10L,
                        now.minusDays(1).minusHours(3),
                        now.minusDays(1));

        assertThat(meeting.isActivityEnded(now)).isTrue();
    }
}
