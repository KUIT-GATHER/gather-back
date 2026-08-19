package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.posting.repository.PostingSearchLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;

/**
 * {@link PostingService#getPostings}는 클래스 자체가 {@code @Transactional(readOnly = true)}라, 그 물리 트랜잭션
 * 안에서 그냥 INSERT를 시도하면 MySQL이 거부한다. 이 테스트는 실제 DB에 대해 (테스트 메서드 자체를 트랜잭션으로 감싸지 않고) {@code
 * getPostings}를 직접 호출해서, REQUIRES_NEW로 분리한 로깅이 readOnly 트랜잭션 안에서도 실제로 커밋되는지 검증한다.
 */
@SpringBootTest
class PostingSearchLogServiceIntegrationTest {

    @Autowired private PostingService postingService;
    @Autowired private PostingSearchLogRepository postingSearchLogRepository;

    private static final String KEYWORD = "통합테스트키워드";

    @AfterEach
    void cleanUp() {
        postingSearchLogRepository.findAll().stream()
                .filter(log -> KEYWORD.equals(log.getKeyword()))
                .forEach(postingSearchLogRepository::delete);
    }

    @Test
    void getPostings_persistsSearchLog_despiteReadOnlyOuterTransaction() {
        postingService.getPostings(
                Sort.by(Sort.Direction.DESC, "id"),
                null,
                20,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                KEYWORD,
                null);

        boolean logged =
                postingSearchLogRepository.findAll().stream()
                        .anyMatch(log -> KEYWORD.equals(log.getKeyword()));
        assertThat(logged).isTrue();
    }
}
