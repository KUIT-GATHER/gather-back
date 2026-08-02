package com.gather.gather.domain.post.entity;

/** 게시글 이미지 업로드 세션 상태. PENDING(발급됨, 미반영) → APPLIED(게시글에 반영됨). */
public enum PostImageUploadStatus {
    PENDING,
    APPLIED
}
