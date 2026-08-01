package com.gather.gather.domain.posting.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostingTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 1);

    @Test
    @DisplayName("actEndDate가 오늘 이전이면 활동 종료 상태다")
    void isActivityEnded_returnsTrue_whenActEndDateIsBeforeToday() {
        Posting posting = postingWith(TODAY.minusDays(1), null);

        assertThat(posting.isActivityEnded(TODAY)).isTrue();
    }

    @Test
    @DisplayName("actEndDate가 오늘 이후면 활동 종료 상태가 아니다")
    void isActivityEnded_returnsFalse_whenActEndDateIsAfterToday() {
        Posting posting = postingWith(TODAY.plusDays(1), null);

        assertThat(posting.isActivityEnded(TODAY)).isFalse();
    }

    @Test
    @DisplayName("actEndDate가 없는 개별활동일 공고는 activityDate가 지나면 활동 종료 상태다")
    void isActivityEnded_returnsTrue_whenActEndDateMissingAndActivityDateIsBeforeToday() {
        Posting posting = postingWith(null, TODAY.minusDays(1));

        assertThat(posting.isActivityEnded(TODAY)).isTrue();
    }

    @Test
    @DisplayName("actEndDate가 없는 개별활동일 공고는 activityDate가 남아있으면 활동 종료 상태가 아니다")
    void isActivityEnded_returnsFalse_whenActEndDateMissingAndActivityDateIsAfterToday() {
        Posting posting = postingWith(null, TODAY.plusDays(1));

        assertThat(posting.isActivityEnded(TODAY)).isFalse();
    }

    @Test
    @DisplayName("actEndDate와 activityDate가 모두 없으면 예외 없이 활동 종료 상태가 아니다")
    void isActivityEnded_returnsFalse_whenBothDatesMissing() {
        Posting posting = postingWith(null, null);

        assertThat(posting.isActivityEnded(TODAY)).isFalse();
    }

    private Posting postingWith(LocalDate actEndDate, LocalDate activityDate) {
        return Posting.builder()
                .title("테스트 공고")
                .status(PostingStatus.RECRUITING)
                .activityDate(activityDate)
                .actEndDate(actEndDate)
                .category(PostingCategory.ENVIRONMENT)
                .build();
    }
}
