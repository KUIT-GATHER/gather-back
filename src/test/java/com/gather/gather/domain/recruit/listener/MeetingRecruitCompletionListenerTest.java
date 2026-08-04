package com.gather.gather.domain.recruit.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.badge.event.MeetingCompletedEvent;
import com.gather.gather.domain.post.enums.PostType;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import com.gather.gather.domain.recruit.repository.MeetingRecruitParticipationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetingRecruitCompletionListenerTest {

    @Mock private MeetingRecruitParticipationRepository participationRepository;

    private MeetingRecruitCompletionListener listener;

    @BeforeEach
    void setUp() {
        listener = new MeetingRecruitCompletionListener(participationRepository);
    }

    @Test
    @DisplayName("모임 완료 시 해당 모임의 신청 참여를 봉사완료로 일괄 전환한다")
    void onMeetingCompleted_marksAppliedAsCompleted() {
        listener.onMeetingCompleted(new MeetingCompletedEvent(12L));

        verify(participationRepository)
                .updateStatusByMeeting(
                        12L,
                        MeetingRecruitParticipationStatus.APPLIED,
                        MeetingRecruitParticipationStatus.COMPLETED,
                        PostType.RECRUIT);
    }

    @Test
    @DisplayName("전환 중 예외가 나도 삼켜서 모임 완료 처리에 영향을 주지 않는다")
    void onMeetingCompleted_swallowsException() {
        when(participationRepository.updateStatusByMeeting(
                        eq(12L), any(), any(), eq(PostType.RECRUIT)))
                .thenThrow(new RuntimeException("bulk update failed"));

        assertThatCode(() -> listener.onMeetingCompleted(new MeetingCompletedEvent(12L)))
                .doesNotThrowAnyException();
    }
}
