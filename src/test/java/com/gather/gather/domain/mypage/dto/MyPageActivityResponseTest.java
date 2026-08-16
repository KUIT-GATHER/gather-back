package com.gather.gather.domain.mypage.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MyPageActivityResponseTest {

    private static final Long USER_ID = 1L;

    @Test
    @DisplayName(
            "ofVolunteer maps an ended APPLIED/CONFIRMED participation's action to NONE, not"
                    + " CANCEL — CANCEL would let the cancel API physically delete an already"
                    + " finished participation")
    void ofVolunteer_mapsEndedParticipationActionToNone() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Posting endedPosting = posting(1L, yesterday.minusDays(4), yesterday);
        PostingParticipation endedParticipation = PostingParticipation.create(USER_ID, 1L);
        ReflectionTestUtils.setField(
                endedParticipation, "status", PostingParticipationStatus.APPLIED);
        ReflectionTestUtils.setField(endedParticipation, "participationEndDate", yesterday);

        MyPageActivityResponse response =
                MyPageActivityResponse.ofVolunteer(endedParticipation, endedPosting);

        assertThat(response.participationAction()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("ofVolunteer keeps CANCEL for a still-ongoing APPLIED/CONFIRMED participation")
    void ofVolunteer_keepsCancelForOngoingParticipation() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Posting ongoingPosting = posting(2L, LocalDate.now(), tomorrow);
        PostingParticipation ongoingParticipation = PostingParticipation.create(USER_ID, 2L);
        ReflectionTestUtils.setField(
                ongoingParticipation, "status", PostingParticipationStatus.APPLIED);
        ReflectionTestUtils.setField(ongoingParticipation, "participationEndDate", tomorrow);

        MyPageActivityResponse response =
                MyPageActivityResponse.ofVolunteer(ongoingParticipation, ongoingPosting);

        assertThat(response.participationAction()).isEqualTo("CANCEL");
    }

    private Posting posting(Long id, LocalDate actStartDate, LocalDate actEndDate) {
        Posting createdPosting =
                Posting.builder()
                        .title("테스트 공고 " + id)
                        .status(PostingStatus.RECRUITING)
                        .activityDate(actStartDate)
                        .actStartDate(actStartDate)
                        .actEndDate(actEndDate)
                        .category(PostingCategory.ENVIRONMENT)
                        .build();
        ReflectionTestUtils.setField(createdPosting, "id", id);
        return createdPosting;
    }
}
