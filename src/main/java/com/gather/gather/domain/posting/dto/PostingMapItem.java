package com.gather.gather.domain.posting.dto;

import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 봉사공고 지도 조회(#186) 응답 항목. 정책상 지도에는 일반 봉사공고(POSTING)만 노출하고 모임 모집공고(MEETING_RECRUIT)는 제외한다.
 *
 * <p>한 공고가 여러 활동장소를 가질 수 있어 공고 단위로 {@code locations} 배열을 내려준다(위·경도가 없는 장소는 제외). 지도 bounds 안에 포함되는지는
 * 이 장소들 중 하나라도 bounds 안에 있는지로 판단하며, {@code locations} 배열 자체는 해당 공고의 유효한(위·경도가 있는) 장소를 bounds 밖 여부와
 * 무관하게 전부 포함한다.
 */
public record PostingMapItem(
        Long id,
        String title,
        String organizationName,
        Long regionId,
        String regionName,
        LocalDateTime activityStartAt,
        LocalDateTime activityEndAt,
        LocalDateTime applyDeadlineAt,
        PostingCategory category,
        PostingStatus status,
        List<PostingLocationResponse> locations) {}
