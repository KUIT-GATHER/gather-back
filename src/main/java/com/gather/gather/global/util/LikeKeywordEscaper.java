package com.gather.gather.global.util;

public final class LikeKeywordEscaper {

    public static final char ESCAPE_CHAR = '\\';

    private LikeKeywordEscaper() {}

    /**
     * JPQL {@code LIKE} 패턴에 그대로 흘려보내면 {@code %}, {@code _}가 와일드카드로 해석되어 사용자가 기대한 리터럴 부분 일치보다 넓은 결과가
     * 나올 수 있다. 저장소 쪽 쿼리에는 {@code escape '\'}를 명시해 이 이스케이프와 짝을 맞춰야 한다.
     */
    public static String escape(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
