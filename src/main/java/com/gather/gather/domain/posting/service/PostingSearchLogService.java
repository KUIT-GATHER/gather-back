package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.entity.PostingSearchLog;
import com.gather.gather.domain.posting.repository.PostingSearchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostingSearchLogService {

    private final PostingSearchLogRepository postingSearchLogRepository;

    /** {@code posting_search_log.keyword} 컬럼 길이(VARCHAR(100))와 맞춘다. */
    private static final int MAX_KEYWORD_LENGTH = 100;

    /**
     * 검색 요청을 호출하는 {@link PostingService#getPostings}는 readOnly 트랜잭션이라, 같은 트랜잭션에서 INSERT를 시도하면
     * MySQL이 거부한다. REQUIRES_NEW로 별도 트랜잭션을 열고, 로깅 실패가 검색 응답에 영향을 주지 않도록 예외를 여기서 흡수한다.
     *
     * <p>컬럼 길이를 넘는 검색어는 INSERT가 실패할 게 확실하므로 시도 자체를 생략한다. 실패 로그에는 검색어 원문 대신 길이만 남긴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String keyword) {
        if (keyword.length() > MAX_KEYWORD_LENGTH) {
            log.warn("검색어 로깅 생략. keyword 길이={} (최대 {})", keyword.length(), MAX_KEYWORD_LENGTH);
            return;
        }
        try {
            postingSearchLogRepository.save(PostingSearchLog.builder().keyword(keyword).build());
        } catch (RuntimeException e) {
            log.warn("검색어 로깅 실패. keyword 길이={}", keyword.length(), e);
        }
    }
}
