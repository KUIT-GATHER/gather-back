package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.meeting.entity.MeetingImage;
import com.gather.gather.domain.meeting.repository.MeetingImageRepository;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 모임별 대표 이미지(sortOrder가 가장 앞선 1장) URL을 meetingId 기준으로 배치 조회한다.
 *
 * <p>모임마다 {@link MeetingImageRepository}를 개별 호출하지 않도록 meetingId를 모아 한 번에 조회하며,
 * 모임 목록 응답({@code MeetingService}, {@code MeetingBookmarkService},
 * {@code MeetingRecommendationService})과 알림 대표 이미지 조회({@code NotificationThumbnailResolver})가
 * 각자 구현하던 동일한 배치 조회 로직을 공용 컴포넌트로 추출한 것이다.
 */
@Component
@RequiredArgsConstructor
public class MeetingThumbnailResolver {

    private final MeetingImageRepository meetingImageRepository;
    private final MeetingImageUrlResolver meetingImageUrlResolver;

    /** meetingId -> 대표 이미지 URL. 등록된 이미지가 없는 모임은 결과 맵에 포함되지 않는다(호출부에서 get() 시 null). */
    public Map<Long, String> resolve(Collection<Long> meetingIds) {
        if (meetingIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return meetingImageRepository.findRepresentativeImagesByMeetingIds(meetingIds).stream()
                .collect(
                        Collectors.toMap(
                                MeetingImage::getMeetingId,
                                image -> meetingImageUrlResolver.resolve(image.getObjectKey()),
                                // sortOrder가 같은 이미지가 동률로 여러 장 조회되는 극단적인 경우를 대비해
                                // 첫 번째 값을 채택한다(NotificationThumbnailResolver의 기존 방어 로직과 동일).
                                (first, ignored) -> first));
    }
}
