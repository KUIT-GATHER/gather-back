package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.entity.PostingRecommendedKeyword;
import com.gather.gather.domain.posting.repository.PostingRecommendedKeywordRepository;
import com.gather.gather.domain.posting.repository.PostingSearchLogRepository;
import com.gather.gather.global.util.NoriKeywordTokenizer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostingKeywordRecommendationService {

    private static final int AGGREGATION_WINDOW_DAYS = 60;
    private static final int LOG_RETENTION_DAYS = 60;
    private static final int TOP_KEYWORD_COUNT = 10;

    /** {@code posting_recommended_keyword.keyword} 컬럼 길이(VARCHAR(50))와 맞춘다. */
    private static final int MAX_RECOMMENDED_KEYWORD_LENGTH = 50;

    /** 봉사공고 검색 사이트에서는 당연히 등장하는 도메인 공통어라, 추천검색어로서 변별력이 없어 집계에서 제외한다. */
    private static final Set<String> STOPWORDS = Set.of("봉사", "활동", "모집", "신청", "참여", "공고");

    private final PostingSearchLogRepository postingSearchLogRepository;
    private final PostingRecommendedKeywordRepository postingRecommendedKeywordRepository;
    private final NoriKeywordTokenizer noriKeywordTokenizer;

    /** 최근 60일 검색 로그를 형태소 분석해 명사 토큰 빈도 상위 10개로 추천검색어 테이블을 재구성한다. */
    @Transactional
    public int aggregate() {
        LocalDateTime since = LocalDateTime.now().minusDays(AGGREGATION_WINDOW_DAYS);
        List<String> keywords = postingSearchLogRepository.findKeywordsBySearchedAtAfter(since);

        Map<String, Integer> tokenCounts = new HashMap<>();
        for (String keyword : keywords) {
            for (String token : noriKeywordTokenizer.tokenize(keyword)) {
                if (token.length() > MAX_RECOMMENDED_KEYWORD_LENGTH || STOPWORDS.contains(token)) {
                    continue;
                }
                tokenCounts.merge(token, 1, Integer::sum);
            }
        }

        List<PostingRecommendedKeyword> topKeywords =
                tokenCounts.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(TOP_KEYWORD_COUNT)
                        .map(
                                entry ->
                                        PostingRecommendedKeyword.builder()
                                                .keyword(entry.getKey())
                                                .score(entry.getValue())
                                                .build())
                        .toList();

        postingRecommendedKeywordRepository.deleteAllInBatch();
        postingRecommendedKeywordRepository.saveAll(topKeywords);
        return topKeywords.size();
    }

    /** 60일 초과 검색 로그를 정리해 posting_search_log가 무한히 커지지 않게 한다. */
    @Transactional
    public void cleanupOldLogs() {
        LocalDateTime before = LocalDateTime.now().minusDays(LOG_RETENTION_DAYS);
        postingSearchLogRepository.deleteBySearchedAtBefore(before);
    }

    @Transactional(readOnly = true)
    public List<String> getRecommendedKeywords() {
        return postingRecommendedKeywordRepository.findAllByOrderByScoreDesc().stream()
                .map(PostingRecommendedKeyword::getKeyword)
                .toList();
    }
}
