package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.meeting.entity.MeetingRecommendedKeyword;
import com.gather.gather.domain.meeting.repository.MeetingRecommendedKeywordRepository;
import com.gather.gather.domain.meeting.repository.MeetingSearchLogRepository;
import com.gather.gather.global.util.NoriKeywordTokenizer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeetingKeywordRecommendationService {

    private static final int AGGREGATION_WINDOW_DAYS = 60;
    private static final int LOG_RETENTION_DAYS = 60;
    private static final int TOP_KEYWORD_COUNT = 10;
    private static final int MAX_RECOMMENDED_KEYWORD_LENGTH = 50;
    private static final List<String> FIXED_RECOMMENDED_KEYWORDS =
            List.of("플로깅", "러닝", "독서", "스터디", "멘토링", "문화", "환경", "유기견", "아동", "노인");
    private static final List<String> STOPWORDS =
            List.of("봉사", "활동", "모집", "모임", "참여", "지원", "신청", "사람", "함께");

    private final MeetingSearchLogRepository meetingSearchLogRepository;
    private final MeetingRecommendedKeywordRepository meetingRecommendedKeywordRepository;
    private final NoriKeywordTokenizer noriKeywordTokenizer;

    @Transactional
    public int aggregate() {
        LocalDateTime since = LocalDateTime.now().minusDays(AGGREGATION_WINDOW_DAYS);
        List<String> keywords = meetingSearchLogRepository.findKeywordsBySearchedAtAfter(since);

        Map<String, Integer> tokenCounts = new HashMap<>();
        for (String keyword : keywords) {
            for (String token : noriKeywordTokenizer.tokenize(keyword)) {
                if (STOPWORDS.contains(token)) {
                    continue;
                }

                if (token.length() > MAX_RECOMMENDED_KEYWORD_LENGTH) {
                    continue;
                }

                tokenCounts.merge(token, 1, Integer::sum);
            }
        }

        List<MeetingRecommendedKeyword> topKeywords =
                tokenCounts.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(TOP_KEYWORD_COUNT)
                        .map(
                                entry ->
                                        MeetingRecommendedKeyword.builder()
                                                .keyword(entry.getKey())
                                                .score(entry.getValue())
                                                .build())
                        .toList();

        meetingRecommendedKeywordRepository.deleteAllInBatch();
        meetingRecommendedKeywordRepository.saveAll(topKeywords);
        return topKeywords.size();
    }

    @Transactional
    public void cleanupOldLogs() {
        LocalDateTime before = LocalDateTime.now().minusDays(LOG_RETENTION_DAYS);
        meetingSearchLogRepository.deleteBySearchedAtBefore(before);
    }

    public List<String> getRecommendedKeywords() {
        return FIXED_RECOMMENDED_KEYWORDS;
    }
}
