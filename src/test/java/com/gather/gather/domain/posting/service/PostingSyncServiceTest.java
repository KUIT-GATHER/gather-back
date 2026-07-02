package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.posting.client.VolunteerApiClient;
import com.gather.gather.domain.posting.client.VolunteerApiException;
import com.gather.gather.domain.posting.client.dto.VolunteerApiItemDto;
import com.gather.gather.domain.posting.client.dto.VolunteerApiSearchCondition;
import com.gather.gather.domain.posting.client.dto.VolunteerApiSearchItemDto;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostingSyncServiceTest {

    @Mock private VolunteerApiClient volunteerApiClient;
    @Mock private PostingRepository postingRepository;
    @Mock private RegionRepository regionRepository;

    private PostingSyncService postingSyncService;

    @BeforeEach
    void setUp() {
        postingSyncService = new PostingSyncService(volunteerApiClient, postingRepository, regionRepository);
    }

    @Test
    @DisplayName("new progrmRegistNo triggers a detail lookup and inserts a fully mapped Posting")
    void syncRecentPostings_insertsNewPosting_whenExtIdNotFound() {
        when(volunteerApiClient.searchList(any(), eq(1), eq(100)))
                .thenReturn(List.of(searchItem("100", "2")));
        when(postingRepository.findByExtId("100")).thenReturn(Optional.empty());
        when(volunteerApiClient.getItem("100")).thenReturn(detailItem("100", "2", "20260601"));
        when(regionRepository.findByCode("3020000"))
                .thenReturn(Optional.of(regionWithId(5L, "3020000")));

        PostingSyncResult result = postingSyncService.syncRecentPostings();

        ArgumentCaptor<Posting> captor = ArgumentCaptor.forClass(Posting.class);
        verify(postingRepository).save(captor.capture());
        Posting saved = captor.getValue();
        assertThat(saved.getExtId()).isEqualTo("100");
        assertThat(saved.getTitle()).isEqualTo("제목-100");
        assertThat(saved.getStatus()).isEqualTo(PostingStatus.RECRUITING);
        assertThat(saved.getRegionId()).isEqualTo(5L);
        assertThat(saved.getIsActive()).isTrue();
        assertThat(result).isEqualTo(new PostingSyncResult(1, 1, 0, 0));
    }

    @Test
    @DisplayName("existing extId only applies list-response fields and never calls the detail endpoint")
    void syncRecentPostings_updatesExisting_withoutCallingDetailEndpoint() {
        Posting existing = existingPosting("200");
        when(volunteerApiClient.searchList(any(), eq(1), eq(100)))
                .thenReturn(List.of(searchItem("200", "3")));
        when(postingRepository.findByExtId("200")).thenReturn(Optional.of(existing));

        PostingSyncResult result = postingSyncService.syncRecentPostings();

        verify(volunteerApiClient, never()).getItem(any());
        assertThat(existing.getTitle()).isEqualTo("제목-200");
        assertThat(existing.getStatus()).isEqualTo(PostingStatus.CLOSED);
        assertThat(existing.getIsActive()).isTrue();
        assertThat(result).isEqualTo(new PostingSyncResult(1, 0, 1, 0));
    }

    @Test
    @DisplayName("pagination continues while a page is full and stops once a short page is returned")
    void syncRecentPostings_paginatesUntilShortPage() {
        List<VolunteerApiSearchItemDto> fullPage =
                IntStream.rangeClosed(1, 100)
                        .mapToObj(i -> searchItem(String.valueOf(i), "2"))
                        .toList();
        List<VolunteerApiSearchItemDto> lastPage = List.of(searchItem("101", "2"));

        when(volunteerApiClient.searchList(any(), anyInt(), eq(100)))
                .thenReturn(fullPage, lastPage);
        when(postingRepository.findByExtId(any())).thenReturn(Optional.of(existingPosting("x")));

        PostingSyncResult result = postingSyncService.syncRecentPostings();

        verify(volunteerApiClient).searchList(any(), eq(1), eq(100));
        verify(volunteerApiClient).searchList(any(), eq(2), eq(100));
        verify(volunteerApiClient, times(2)).searchList(any(VolunteerApiSearchCondition.class), anyInt(), eq(100));
        assertThat(result.scanned()).isEqualTo(101);
    }

    @Test
    @DisplayName("a failure on one item is logged and skipped without aborting the rest of the batch")
    void syncRecentPostings_isolatesPerItemFailure() {
        when(volunteerApiClient.searchList(any(), eq(1), eq(100)))
                .thenReturn(List.of(searchItem("A", "2"), searchItem("B", "2")));
        when(postingRepository.findByExtId("A")).thenReturn(Optional.empty());
        when(postingRepository.findByExtId("B")).thenReturn(Optional.empty());
        when(volunteerApiClient.getItem("A")).thenThrow(new VolunteerApiException("boom"));
        when(volunteerApiClient.getItem("B")).thenReturn(detailItem("B", "2", "20260601"));

        PostingSyncResult result = postingSyncService.syncRecentPostings();

        verify(postingRepository, times(1)).save(any());
        assertThat(result).isEqualTo(new PostingSyncResult(2, 1, 0, 1));
    }

    @Test
    @DisplayName("a new posting with no parsable activityDate is skipped instead of failing the batch")
    void syncRecentPostings_skipsNewPosting_whenActivityDateMissing() {
        when(volunteerApiClient.searchList(any(), eq(1), eq(100)))
                .thenReturn(List.of(searchItem("Z", "2")));
        when(postingRepository.findByExtId("Z")).thenReturn(Optional.empty());
        when(volunteerApiClient.getItem("Z")).thenReturn(detailItem("Z", "2", null));

        PostingSyncResult result = postingSyncService.syncRecentPostings();

        verify(postingRepository, never()).save(any());
        assertThat(result).isEqualTo(new PostingSyncResult(1, 0, 0, 1));
    }

    @ParameterizedTest(name = "progrmSttusSe={0} maps to {1}")
    @CsvSource({
        "1, RECRUITING",
        "2, RECRUITING",
        "3, CLOSED",
        "9, RECRUITING",
    })
    @DisplayName("mapStatus follows the assumed 1365 status-code convention, defaulting unknown codes to RECRUITING")
    void syncRecentPostings_mapsStatusCode(String code, PostingStatus expected) {
        when(volunteerApiClient.searchList(any(), eq(1), eq(100)))
                .thenReturn(List.of(searchItem("S-" + code, code)));
        when(postingRepository.findByExtId(any())).thenReturn(Optional.empty());
        when(volunteerApiClient.getItem(any())).thenReturn(detailItem("S-" + code, code, "20260601"));

        postingSyncService.syncRecentPostings();

        ArgumentCaptor<Posting> captor = ArgumentCaptor.forClass(Posting.class);
        verify(postingRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(expected);
    }

    @Test
    @DisplayName("resolveRegionId prefers gugunCd match over sidoCd")
    void syncRecentPostings_prefersGugunCdOverSidoCd() {
        when(volunteerApiClient.searchList(any(), eq(1), eq(100)))
                .thenReturn(List.of(searchItem("R1", "2")));
        when(postingRepository.findByExtId("R1")).thenReturn(Optional.empty());
        when(volunteerApiClient.getItem("R1")).thenReturn(detailItem("R1", "2", "20260601"));
        when(regionRepository.findByCode("3020000")).thenReturn(Optional.of(regionWithId(7L, "3020000")));

        postingSyncService.syncRecentPostings();

        ArgumentCaptor<Posting> captor = ArgumentCaptor.forClass(Posting.class);
        verify(postingRepository).save(captor.capture());
        assertThat(captor.getValue().getRegionId()).isEqualTo(7L);
        verify(regionRepository, never()).findByCode("6110000");
    }

    @Test
    @DisplayName("resolveRegionId falls back to sidoCd when gugunCd has no match")
    void syncRecentPostings_fallsBackToSidoCd_whenGugunCdNotFound() {
        when(volunteerApiClient.searchList(any(), eq(1), eq(100)))
                .thenReturn(List.of(searchItem("R2", "2")));
        when(postingRepository.findByExtId("R2")).thenReturn(Optional.empty());
        when(volunteerApiClient.getItem("R2")).thenReturn(detailItem("R2", "2", "20260601"));
        when(regionRepository.findByCode("3020000")).thenReturn(Optional.empty());
        when(regionRepository.findByCode("6110000")).thenReturn(Optional.of(regionWithId(9L, "6110000")));

        postingSyncService.syncRecentPostings();

        ArgumentCaptor<Posting> captor = ArgumentCaptor.forClass(Posting.class);
        verify(postingRepository).save(captor.capture());
        assertThat(captor.getValue().getRegionId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("resolveRegionId returns null when neither gugunCd nor sidoCd match")
    void syncRecentPostings_returnsNullRegion_whenNeitherCodeMatches() {
        when(volunteerApiClient.searchList(any(), eq(1), eq(100)))
                .thenReturn(List.of(searchItem("R3", "2")));
        when(postingRepository.findByExtId("R3")).thenReturn(Optional.empty());
        when(volunteerApiClient.getItem("R3")).thenReturn(detailItem("R3", "2", "20260601"));

        postingSyncService.syncRecentPostings();

        ArgumentCaptor<Posting> captor = ArgumentCaptor.forClass(Posting.class);
        verify(postingRepository).save(captor.capture());
        assertThat(captor.getValue().getRegionId()).isNull();
    }

    private Posting existingPosting(String extId) {
        return Posting.builder()
                .extId(extId)
                .title("기존 제목")
                .status(PostingStatus.RECRUITING)
                .activityDate(LocalDate.of(2026, 1, 1))
                .categoryId(1L)
                .build();
    }

    private Region regionWithId(Long id, String code) {
        Region region = Region.create("테스트지역", 3, code, null);
        org.springframework.test.util.ReflectionTestUtils.setField(region, "id", id);
        return region;
    }

    private VolunteerApiSearchItemDto searchItem(String progrmRegistNo, String progrmSttusSe) {
        return searchItem(progrmRegistNo, progrmSttusSe, "6110000", "3020000");
    }

    private VolunteerApiSearchItemDto searchItem(
            String progrmRegistNo, String progrmSttusSe, String sidoCd, String gugunCd) {
        return new VolunteerApiSearchItemDto(
                "09",
                "18",
                "행복복지관",
                "Y",
                gugunCd,
                "행복재단",
                "20260101",
                "20260901",
                "20260601",
                "20260901",
                progrmRegistNo,
                "제목-" + progrmRegistNo,
                progrmSttusSe,
                sidoCd,
                "생활편의",
                "http://example.com",
                "N");
    }

    private VolunteerApiItemDto detailItem(String progrmRegistNo, String progrmSttusSe, String progrmBgnde) {
        return detailItem(progrmRegistNo, progrmSttusSe, progrmBgnde, "6110000", "3020000");
    }

    private VolunteerApiItemDto detailItem(
            String progrmRegistNo,
            String progrmSttusSe,
            String progrmBgnde,
            String sidoCd,
            String gugunCd) {
        return new VolunteerApiItemDto(
                "09",
                "18",
                "행복복지관",
                "0010000",
                "Y",
                "3",
                "서울시 어딘가 1",
                null,
                null,
                "35.53,129.41",
                null,
                null,
                "test@example.com",
                "N",
                "02-000-0000",
                "N",
                gugunCd,
                "행복모집기관",
                "행복재단",
                "홍길동",
                "20260101",
                "20260901",
                "N",
                "서울시 어딘가",
                progrmBgnde,
                "내용입니다",
                "20260901",
                progrmRegistNo,
                "제목-" + progrmRegistNo,
                progrmSttusSe,
                "5",
                sidoCd,
                "생활편의",
                "02-111-1111",
                "N");
    }
}
