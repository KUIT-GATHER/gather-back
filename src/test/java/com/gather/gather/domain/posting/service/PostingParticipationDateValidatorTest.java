package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostingParticipationDateValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 13);

    @Test
    @DisplayName("validate passes for a single-day date within the posting period")
    void validate_passes_forSingleDayWithinPeriod() {
        Posting posting = postingWithPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThatCode(
                        () ->
                                PostingParticipationDateValidator.validate(
                                        posting,
                                        LocalDate.of(2026, 8, 15),
                                        LocalDate.of(2026, 8, 15),
                                        TODAY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validate passes for a multi-day range within the posting period")
    void validate_passes_forMultiDayRangeWithinPeriod() {
        Posting posting = postingWithPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThatCode(
                        () ->
                                PostingParticipationDateValidator.validate(
                                        posting,
                                        LocalDate.of(2026, 8, 15),
                                        LocalDate.of(2026, 8, 18),
                                        TODAY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validate throws PARTICIPATION_DATE_INVALID_RANGE when start is after end")
    void validate_throwsInvalidRange_whenStartAfterEnd() {
        Posting posting = postingWithPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThatThrownBy(
                        () ->
                                PostingParticipationDateValidator.validate(
                                        posting,
                                        LocalDate.of(2026, 8, 18),
                                        LocalDate.of(2026, 8, 15),
                                        TODAY))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", ErrorCode.PARTICIPATION_DATE_INVALID_RANGE);
    }

    @Test
    @DisplayName("validate throws PARTICIPATION_DATE_IN_PAST when start date is before today")
    void validate_throwsDateInPast_whenStartBeforeToday() {
        Posting posting = postingWithPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThatThrownBy(
                        () ->
                                PostingParticipationDateValidator.validate(
                                        posting,
                                        LocalDate.of(2026, 8, 1),
                                        LocalDate.of(2026, 8, 5),
                                        TODAY))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPATION_DATE_IN_PAST);
    }

    @Test
    @DisplayName(
            "validate throws PARTICIPATION_DATE_OUT_OF_POSTING_PERIOD when range starts before"
                    + " the posting period")
    void validate_throwsOutOfPeriod_whenStartBeforePostingPeriod() {
        Posting posting = postingWithPeriod(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 31));

        assertThatThrownBy(
                        () ->
                                PostingParticipationDateValidator.validate(
                                        posting,
                                        LocalDate.of(2026, 8, 15),
                                        LocalDate.of(2026, 8, 25),
                                        LocalDate.of(2026, 8, 1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", ErrorCode.PARTICIPATION_DATE_OUT_OF_POSTING_PERIOD);
    }

    @Test
    @DisplayName(
            "validate throws PARTICIPATION_DATE_OUT_OF_POSTING_PERIOD when range ends after the"
                    + " posting period")
    void validate_throwsOutOfPeriod_whenEndAfterPostingPeriod() {
        Posting posting = postingWithPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20));

        assertThatThrownBy(
                        () ->
                                PostingParticipationDateValidator.validate(
                                        posting,
                                        LocalDate.of(2026, 8, 18),
                                        LocalDate.of(2026, 8, 25),
                                        TODAY))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", ErrorCode.PARTICIPATION_DATE_OUT_OF_POSTING_PERIOD);
    }

    private Posting postingWithPeriod(LocalDate actStartDate, LocalDate actEndDate) {
        return Posting.builder()
                .title("테스트 공고")
                .status(PostingStatus.RECRUITING)
                .activityDate(actStartDate)
                .actStartDate(actStartDate)
                .actEndDate(actEndDate)
                .category(PostingCategory.ENVIRONMENT)
                .isActive(true)
                .build();
    }
}
