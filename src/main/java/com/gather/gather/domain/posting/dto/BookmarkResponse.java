package com.gather.gather.domain.posting.dto;

public record BookmarkResponse(Long postingId, boolean bookmarked) {

    public static BookmarkResponse of(Long postingId, boolean bookmarked) {
        return new BookmarkResponse(postingId, bookmarked);
    }
}
