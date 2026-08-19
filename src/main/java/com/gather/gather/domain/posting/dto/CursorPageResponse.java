package com.gather.gather.global.common;

import java.util.List;

/**
 * 커서 기반(키셋) 페이지네이션 응답. 무한스크롤처럼 순차 진행만 필요한 목록에서 사용한다.
 *
 * <p>{@link PageResponse}(OFFSET 기반)와 달리 총 개수(totalElements)·총 페이지(totalPages)를 계산하지 않는다 — 이 값들은
 * 무한스크롤에 필요 없고, 계산하려면 별도 COUNT 쿼리가 필요해 키셋 페이지네이션의 성능 이점을 스스로 없애 버린다. 대신 다음 페이지 존재 여부(hasNext)와 다음
 * 요청에 그대로 넘기면 되는 불투명 커서(nextCursor)만 내려준다.
 */
public record CursorPageResponse<T>(List<T> content, String nextCursor, boolean hasNext) {

    public static <T> CursorPageResponse<T> of(
            List<T> content, String nextCursor, boolean hasNext) {
        return new CursorPageResponse<>(content, nextCursor, hasNext);
    }
}
