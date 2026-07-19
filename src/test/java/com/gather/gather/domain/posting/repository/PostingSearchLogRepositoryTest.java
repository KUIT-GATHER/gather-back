package com.gather.gather.domain.posting.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.posting.entity.PostingSearchLog;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 실제 DB에서 최근 로그 조회와 오래된 로그 삭제(명시적 JPQL bulk delete)가 정상 동작하는지 검증한다. */
@SpringBootTest
@Transactional
class PostingSearchLogRepositoryTest {

    @Autowired private PostingSearchLogRepository postingSearchLogRepository;

    @Test
    void findAllBySearchedAtAfter_returnsOnlyLogsWithinWindow() throws Exception {
        save("유기견봉사", LocalDateTime.now().minusDays(10));
        save("환경정화", LocalDateTime.now().minusDays(90));

        List<PostingSearchLog> recent =
                postingSearchLogRepository.findAllBySearchedAtAfter(
                        LocalDateTime.now().minusDays(60));

        assertThat(recent).extracting(PostingSearchLog::getKeyword).containsExactly("유기견봉사");
    }

    @Test
    void deleteBySearchedAtBefore_removesOnlyOldLogs() throws Exception {
        save("유기견봉사", LocalDateTime.now().minusDays(10));
        save("환경정화", LocalDateTime.now().minusDays(90));

        postingSearchLogRepository.deleteBySearchedAtBefore(LocalDateTime.now().minusDays(60));

        List<PostingSearchLog> remaining =
                postingSearchLogRepository.findAllBySearchedAtAfter(
                        LocalDateTime.now().minusDays(365));
        assertThat(remaining).extracting(PostingSearchLog::getKeyword).containsExactly("유기견봉사");
    }

    /** searchedAt이 빌더에서 now()로 고정되므로, 리플렉션으로 과거 시각을 직접 주입해 시간 경계 테스트를 구성한다. */
    private void save(String keyword, LocalDateTime searchedAt) throws Exception {
        PostingSearchLog log = PostingSearchLog.builder().keyword(keyword).build();
        Field field = PostingSearchLog.class.getDeclaredField("searchedAt");
        field.setAccessible(true);
        field.set(log, searchedAt);
        postingSearchLogRepository.save(log);
    }
}
