package com.gather.gather.domain.posting.dto;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingLocation;
import java.math.BigDecimal;

public record PostingLocationResponse(
        Integer locationSeq, String address, BigDecimal latitude, BigDecimal longitude) {

    /** 1번째 장소는 {@link Posting} 자신의 필드로 표현된다(docs/devplan.md 섹션 8.2). */
    public static PostingLocationResponse first(Posting posting) {
        return new PostingLocationResponse(
                1, posting.getPostAddress(), posting.getLatitude(), posting.getLongitude());
    }

    public static PostingLocationResponse from(PostingLocation location) {
        return new PostingLocationResponse(
                location.getLocationSeq(),
                location.getAddress(),
                location.getLatitude(),
                location.getLongitude());
    }
}
