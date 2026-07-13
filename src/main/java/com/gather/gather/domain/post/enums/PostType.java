package com.gather.gather.domain.post.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * 모임 게시글 유형.
 *
 * <ul>
 *   <li>{@code NOTICE} - 공지 (모임장만 작성)
 *   <li>{@code REVIEW} - 활동 후기
 *   <li>{@code RECRUIT} - 모집 글 ({@code recruitCapacity} 사용)
 *   <li>{@code FREE} - 자유 글
 * </ul>
 *
 * <p>미가입자(모임에 가입하지 않은 로그인 사용자)는 {@link #NOTICE}·{@link #REVIEW}만 열람할 수 있다. (게시판 화면 정책)
 */
public enum PostType {
    NOTICE,
    REVIEW,
    RECRUIT,
    FREE;

    /** 미가입자에게 노출 가능한 유형(공지·후기). */
    private static final Set<PostType> VISIBLE_TO_NON_MEMBER = EnumSet.of(NOTICE, REVIEW);

    public static Set<PostType> visibleToNonMember() {
        return EnumSet.copyOf(VISIBLE_TO_NON_MEMBER);
    }

    public boolean isVisibleToNonMember() {
        return VISIBLE_TO_NON_MEMBER.contains(this);
    }

    public boolean isNotice() {
        return this == NOTICE;
    }
}
