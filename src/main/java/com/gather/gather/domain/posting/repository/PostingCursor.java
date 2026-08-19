package com.gather.gather.domain.posting.repository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link UnifiedPostingQueryRepository}의 키셋(커서) 페이지네이션에서 쓰는 불투명(opaque) 커서 인코더/디코더.
 *
 * <p>커서는 현재 요청의 정렬 키 값들(우선순위 버킷 포함)을 등장 순서대로 콤마로 이어붙인 뒤 URL-safe Base64로 감싼 문자열이다. 정렬 파라미터가 바뀌면 정렬
 * 키 구성 자체가 달라지므로, 클라이언트는 항상 최초 요청과 동일한 정렬 파라미터로 커서를 이어서 호출해야 한다(다르면 {@link
 * UnifiedPostingQueryRepository}가 400으로 거부한다). 값의 타입 해석은 호출부(정렬 키 목록)가 담당하므로 여기서는 문자열 토큰만 다룬다.
 */
final class PostingCursor {

    /** 실제 값 안에 등장할 일이 없는 제어문자로 null 값을 표시한다(콤마 자체는 값 안에 절대 등장하지 않는 숫자/ISO-8601 문자열만 다룸). */
    private static final String NULL_TOKEN = "\u0000";

    private static final String DELIMITER = ",";

    private PostingCursor() {}

    static String encode(List<String> tokens) {
        String joined =
                tokens.stream()
                        .map(token -> token == null ? NULL_TOKEN : token)
                        .collect(Collectors.joining(DELIMITER));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(joined.getBytes(StandardCharsets.UTF_8));
    }

    /** 커서 문자열을 토큰 목록으로 되돌린다. 형식이 깨진 커서(위조·변조 등)면 {@link IllegalArgumentException}을 던진다. */
    static List<String> decode(String cursor) {
        byte[] decoded = Base64.getUrlDecoder().decode(cursor);
        String joined = new String(decoded, StandardCharsets.UTF_8);
        String[] parts = joined.split(DELIMITER, -1);
        List<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            tokens.add(NULL_TOKEN.equals(part) ? null : part);
        }
        return tokens;
    }
}
