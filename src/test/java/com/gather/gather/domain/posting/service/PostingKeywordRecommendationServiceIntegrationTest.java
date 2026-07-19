package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.posting.entity.PostingRecommendedKeyword;
import com.gather.gather.domain.posting.entity.PostingSearchLog;
import com.gather.gather.domain.posting.repository.PostingRecommendedKeywordRepository;
import com.gather.gather.domain.posting.repository.PostingSearchLogRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 DB에서 top10 재구성(deleteAllInBatch + saveAll)이 unique 제약(keyword) 충돌 없이 한 트랜잭션 안에서 정상 수행되는지, 두 번
 * 연속 실행해도 안전한지 검증한다.
 */
@SpringBootTest
@Transactional
class PostingKeywordRecommendationServiceIntegrationTest {

    @Autowired private PostingSearchLogRepository postingSearchLogRepository;
    @Autowired private PostingRecommendedKeywordRepository postingRecommendedKeywordRepository;
    @Autowired private PostingKeywordRecommendationService postingKeywordRecommendationService;

    @Test
    void aggregate_replacesExistingTopKeywords_withoutUniqueConstraintViolation() {
        postingSearchLogRepository.save(PostingSearchLog.builder().keyword("환경정화봉사").build());
        postingSearchLogRepository.save(PostingSearchLog.builder().keyword("환경정화").build());
        postingKeywordRecommendationService.aggregate();

        postingSearchLogRepository.save(PostingSearchLog.builder().keyword("환경정화봉사").build());
        int secondRunCount = postingKeywordRecommendationService.aggregate();

        List<PostingRecommendedKeyword> stored =
                postingRecommendedKeywordRepository.findAllByOrderByScoreDesc();
        assertThat(secondRunCount).isEqualTo(stored.size());
        assertThat(stored).extracting(PostingRecommendedKeyword::getKeyword).contains("환경", "정화");
    }
}
