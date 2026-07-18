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

    /**
     * 검색 요청을 호출하는 {@link PostingService#getPostings}는 readOnly 트랜잭션이라, 같은 트랜잭션에서 INSERT를 시도하면
     * MySQL이 거부한다. REQUIRES_NEW로 별도 트랜잭션을 열고, 로깅 실패가 검색 응답에 영향을 주지 않도록 예외를 여기서 흡수한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String keyword) {
        try {
            postingSearchLogRepository.save(PostingSearchLog.builder().keyword(keyword).build());
        } catch (RuntimeException e) {
            log.warn("검색어 로깅 실패. keyword={}", keyword, e);
        }
    }
}
