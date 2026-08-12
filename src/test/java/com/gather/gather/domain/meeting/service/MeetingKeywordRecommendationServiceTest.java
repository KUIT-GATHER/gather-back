package com.gather.gather.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.meeting.entity.MeetingRecommendedKeyword;
import com.gather.gather.domain.meeting.repository.MeetingRecommendedKeywordRepository;
import com.gather.gather.domain.meeting.repository.MeetingSearchLogRepository;
import com.gather.gather.global.util.NoriKeywordTokenizer;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetingKeywordRecommendationServiceTest {

    @Mock private MeetingSearchLogRepository meetingSearchLogRepository;

    @Mock private MeetingRecommendedKeywordRepository meetingRecommendedKeywordRepository;

    @Mock private NoriKeywordTokenizer noriKeywordTokenizer;

    @InjectMocks private MeetingKeywordRecommendationService meetingKeywordRecommendationService;

    @Test
    void aggregate_savesTopKeywordsFromRecentSearchLogs() {
        when(meetingSearchLogRepository.findKeywordsBySearchedAtAfter(
                        org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of("플로깅 모임", "플로깅 활동", "교육 봉사"));

        when(noriKeywordTokenizer.tokenize("플로깅 모임")).thenReturn(List.of("플로깅", "모임"));
        when(noriKeywordTokenizer.tokenize("플로깅 활동")).thenReturn(List.of("플로깅", "활동"));
        when(noriKeywordTokenizer.tokenize("교육 봉사")).thenReturn(List.of("교육", "봉사"));

        int result = meetingKeywordRecommendationService.aggregate();

        ArgumentCaptor<List<MeetingRecommendedKeyword>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(meetingRecommendedKeywordRepository).deleteAllInBatch();
        verify(meetingRecommendedKeywordRepository).saveAll(captor.capture());

        List<MeetingRecommendedKeyword> savedKeywords = captor.getValue();

        assertThat(result).isEqualTo(savedKeywords.size());
        assertThat(savedKeywords)
                .extracting(MeetingRecommendedKeyword::getKeyword)
                .contains("플로깅", "교육")
                .doesNotContain("모임", "활동", "봉사");
    }

    @Test
    void getRecommendedKeywords_returnsFixedKeywords() {
        List<String> result = meetingKeywordRecommendationService.getRecommendedKeywords();

        assertThat(result)
                .containsExactly("플로깅", "러닝", "독서", "스터디", "멘토링", "문화", "환경", "유기견", "아동", "노인");
        verifyNoInteractions(meetingRecommendedKeywordRepository);
    }
}
