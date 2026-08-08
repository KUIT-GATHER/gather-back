package com.gather.gather.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.meeting.entity.MeetingImage;
import com.gather.gather.domain.meeting.repository.MeetingImageRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetingThumbnailResolverTest {

    @Mock private MeetingImageRepository meetingImageRepository;
    @Mock private MeetingImageUrlResolver meetingImageUrlResolver;

    @InjectMocks private MeetingThumbnailResolver meetingThumbnailResolver;

    @Test
    @DisplayName("meetingId별 대표 이미지 URL을 배치로 조회한다")
    void resolve_returnsThumbnailUrlPerMeetingId() {
        MeetingImage image1 = MeetingImage.create(1L, "meetings/1/photo.jpg", 0);
        MeetingImage image2 = MeetingImage.create(2L, "meetings/2/photo.jpg", 0);
        when(meetingImageRepository.findRepresentativeImagesByMeetingIds(List.of(1L, 2L)))
                .thenReturn(List.of(image1, image2));
        when(meetingImageUrlResolver.resolve("meetings/1/photo.jpg"))
                .thenReturn("https://cdn.example.com/meetings/1/photo.jpg");
        when(meetingImageUrlResolver.resolve("meetings/2/photo.jpg"))
                .thenReturn("https://cdn.example.com/meetings/2/photo.jpg");

        Map<Long, String> result = meetingThumbnailResolver.resolve(List.of(1L, 2L));

        assertThat(result)
                .containsEntry(1L, "https://cdn.example.com/meetings/1/photo.jpg")
                .containsEntry(2L, "https://cdn.example.com/meetings/2/photo.jpg");
    }

    @Test
    @DisplayName("등록된 이미지가 없는 모임은 결과 맵에 포함되지 않는다")
    void resolve_omitsMeetingsWithoutRegisteredImage() {
        when(meetingImageRepository.findRepresentativeImagesByMeetingIds(List.of(1L)))
                .thenReturn(List.of());

        Map<Long, String> result = meetingThumbnailResolver.resolve(List.of(1L));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("meetingId가 비어 있으면 리포지토리를 호출하지 않고 빈 맵을 반환한다")
    void resolve_returnsEmptyMapWithoutQuerying_whenMeetingIdsIsEmpty() {
        Map<Long, String> result = meetingThumbnailResolver.resolve(List.of());

        assertThat(result).isEmpty();
        verify(meetingImageRepository, never())
                .findRepresentativeImagesByMeetingIds(anyCollection());
    }
}
