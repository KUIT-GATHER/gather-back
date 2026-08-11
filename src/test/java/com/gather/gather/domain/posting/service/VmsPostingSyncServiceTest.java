package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.posting.crawler.VmsCrawlClient;
import com.gather.gather.domain.posting.crawler.VmsCrawlException;
import com.gather.gather.domain.posting.crawler.VmsCrawlProperties;
import com.gather.gather.domain.posting.crawler.dto.VmsPostingDetail;
import com.gather.gather.domain.posting.crawler.dto.VmsPostingListItem;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingSource;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.PostingRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class VmsPostingSyncServiceTest {

    @Mock private VmsCrawlClient vmsCrawlClient;
    @Mock private PostingRepository postingRepository;
    @Mock private VmsRegionResolver vmsRegionResolver;
    @Mock private PlatformTransactionManager transactionManager;

    private VmsPostingSyncService vmsPostingSyncService;

    @BeforeEach
    void setUp() {
        VmsCrawlProperties properties =
                new VmsCrawlProperties("https://www.vms.or.kr", "test-agent", 1, 0, 30, 100);
        vmsPostingSyncService =
                new VmsPostingSyncService(
                        vmsCrawlClient,
                        properties,
                        postingRepository,
                        vmsRegionResolver,
                        transactionManager);
    }

    @Test
    @DisplayName("신규 seq는 상세조회 후 vms: 접두사 extId로 저장된다")
    void syncRecentPostings_insertsNewPosting_whenExtIdNotFound() {
        when(vmsCrawlClient.fetchList(eq(1), any(), any()))
                .thenReturn(List.of(listItem("517551", "모집중")))
                .thenReturn(List.of());
        when(postingRepository.findByExtId("vms:517551")).thenReturn(Optional.empty());
        when(vmsCrawlClient.fetchDetail("517551")).thenReturn(detail("517551", "모집중"));
        when(vmsRegionResolver.resolve(any())).thenReturn(7L);

        PostingSyncResult result = vmsPostingSyncService.syncRecentPostings();

        ArgumentCaptor<Posting> captor = ArgumentCaptor.forClass(Posting.class);
        verify(postingRepository).save(captor.capture());
        Posting saved = captor.getValue();
        assertThat(saved.getExtId()).isEqualTo("vms:517551");
        assertThat(saved.getSource()).isEqualTo(PostingSource.VMS_CRAWL);
        assertThat(saved.getStatus()).isEqualTo(PostingStatus.RECRUITING);
        assertThat(saved.getActivityDate()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(saved.getActEndDate()).isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(saved.getRecruitCount()).isEqualTo(2);
        assertThat(saved.getApplicantCount()).isEqualTo(1);
        assertThat(saved.getCategory()).isEqualTo(PostingCategory.WELFARE);
        assertThat(saved.getRegionId()).isEqualTo(7L);
        assertThat(saved.getIsActive()).isTrue();
        assertThat(result).isEqualTo(new PostingSyncResult(1, 1, 0, 0, 0));
    }

    @Test
    @DisplayName("이미 존재하는 extId는 목록카드 정보만으로 갱신하고 상세조회하지 않는다")
    void syncRecentPostings_updatesExisting_withoutDetailFetch() {
        Posting existing = existingPosting("vms:517531");
        when(vmsCrawlClient.fetchList(eq(1), any(), any()))
                .thenReturn(List.of(listItem("517531", "모집완료")))
                .thenReturn(List.of());
        when(postingRepository.findByExtId("vms:517531")).thenReturn(Optional.of(existing));

        PostingSyncResult result = vmsPostingSyncService.syncRecentPostings();

        verify(vmsCrawlClient, never()).fetchDetail(any());
        assertThat(existing.getStatus()).isEqualTo(PostingStatus.CLOSED);
        assertThat(existing.getIsActive()).isFalse();
        assertThat(result).isEqualTo(new PostingSyncResult(1, 0, 1, 0, 0));
    }

    @Test
    @DisplayName("실행당 상세조회 상한을 넘긴 신규 seq는 스킵된다")
    void syncRecentPostings_skipsNewPosting_whenDetailLookupBudgetExhausted() {
        VmsCrawlProperties zeroBudgetProperties =
                new VmsCrawlProperties("https://www.vms.or.kr", "test-agent", 1, 0, 30, 0);
        vmsPostingSyncService =
                new VmsPostingSyncService(
                        vmsCrawlClient,
                        zeroBudgetProperties,
                        postingRepository,
                        vmsRegionResolver,
                        transactionManager);
        when(vmsCrawlClient.fetchList(eq(1), any(), any()))
                .thenReturn(List.of(listItem("517551", "모집중")))
                .thenReturn(List.of());
        when(postingRepository.findByExtId("vms:517551")).thenReturn(Optional.empty());

        PostingSyncResult result = vmsPostingSyncService.syncRecentPostings();

        verify(vmsCrawlClient, never()).fetchDetail(any());
        assertThat(result).isEqualTo(new PostingSyncResult(1, 0, 0, 0, 1));
    }

    @Test
    @DisplayName("활동기간 시작일이 없는 상세페이지는 실패로 집계되고 저장되지 않는다")
    void syncRecentPostings_countsFailed_whenActivityDateMissing() {
        when(vmsCrawlClient.fetchList(eq(1), any(), any()))
                .thenReturn(List.of(listItem("999", "모집중")))
                .thenReturn(List.of());
        when(postingRepository.findByExtId("vms:999")).thenReturn(Optional.empty());
        when(vmsCrawlClient.fetchDetail("999"))
                .thenReturn(
                        new VmsPostingDetail(
                                "999",
                                "제목",
                                "모집중",
                                "시설봉사",
                                null,
                                null,
                                "기관",
                                "1명 / 0명",
                                "장소",
                                "[서울] 서울특별시",
                                "담당자",
                                "a@b.com",
                                "010-0000-0000",
                                "내용"));

        PostingSyncResult result = vmsPostingSyncService.syncRecentPostings();

        verify(postingRepository, never()).save(any());
        assertThat(result).isEqualTo(new PostingSyncResult(1, 0, 0, 1, 0));
    }

    @Test
    @DisplayName("목록카드 활동기간 파싱에 실패해도 기존 활동기간을 null로 덮어쓰지 않는다")
    void syncRecentPostings_keepsExistingActPeriod_whenListCardPeriodUnparseable() {
        Posting existing = existingPosting("vms:517531");
        VmsPostingListItem unparseablePeriodItem =
                new VmsPostingListItem("517531", "제목-517531", "기관-517531", "상시모집", "모집중");
        when(vmsCrawlClient.fetchList(eq(1), any(), any()))
                .thenReturn(List.of(unparseablePeriodItem))
                .thenReturn(List.of());
        when(postingRepository.findByExtId("vms:517531")).thenReturn(Optional.of(existing));

        vmsPostingSyncService.syncRecentPostings();

        assertThat(existing.getActStartDate()).isEqualTo(LocalDate.of(2026, 8, 11));
        assertThat(existing.getActEndDate()).isEqualTo(LocalDate.of(2026, 8, 13));
        assertThat(existing.getStatus()).isEqualTo(PostingStatus.RECRUITING);
    }

    @Test
    @DisplayName("상세조회가 실패해도 실행당 상세조회 상한 소진에 반영된다")
    void syncRecentPostings_countsFailedDetailFetch_towardBudget() {
        VmsCrawlProperties oneBudgetProperties =
                new VmsCrawlProperties("https://www.vms.or.kr", "test-agent", 1, 0, 30, 1);
        vmsPostingSyncService =
                new VmsPostingSyncService(
                        vmsCrawlClient,
                        oneBudgetProperties,
                        postingRepository,
                        vmsRegionResolver,
                        transactionManager);
        when(vmsCrawlClient.fetchList(eq(1), any(), any()))
                .thenReturn(List.of(listItem("111", "모집중"), listItem("222", "모집중")))
                .thenReturn(List.of());
        when(postingRepository.findByExtId("vms:111")).thenReturn(Optional.empty());
        when(postingRepository.findByExtId("vms:222")).thenReturn(Optional.empty());
        when(vmsCrawlClient.fetchDetail("111")).thenThrow(new VmsCrawlException("네트워크 오류"));

        PostingSyncResult result = vmsPostingSyncService.syncRecentPostings();

        verify(vmsCrawlClient, never()).fetchDetail("222");
        assertThat(result).isEqualTo(new PostingSyncResult(2, 0, 0, 1, 1));
    }

    @Test
    @DisplayName("maxPages override는 설정값 이내로 목록 페이지 조회 수를 줄인다")
    void syncRecentPostings_limitsPages_whenMaxPagesOverrideGiven() {
        VmsCrawlProperties threePageProperties =
                new VmsCrawlProperties("https://www.vms.or.kr", "test-agent", 3, 0, 30, 100);
        vmsPostingSyncService =
                new VmsPostingSyncService(
                        vmsCrawlClient,
                        threePageProperties,
                        postingRepository,
                        vmsRegionResolver,
                        transactionManager);
        when(vmsCrawlClient.fetchList(eq(1), any(), any()))
                .thenReturn(List.of(listItem("517531", "모집중")));
        when(postingRepository.findByExtId("vms:517531"))
                .thenReturn(Optional.of(existingPosting("vms:517531")));

        vmsPostingSyncService.syncRecentPostings(1, null);

        verify(vmsCrawlClient, never()).fetchList(eq(2), any(), any());
        verify(vmsCrawlClient, never()).fetchList(eq(3), any(), any());
    }

    @Test
    @DisplayName("maxDetailLookups override가 설정값보다 크면 설정값으로 제한된다")
    void syncRecentPostings_clampsMaxDetailLookupsOverride_toConfiguredValue() {
        when(vmsCrawlClient.fetchList(eq(1), any(), any()))
                .thenReturn(List.of(listItem("517551", "모집중")))
                .thenReturn(List.of());
        when(postingRepository.findByExtId("vms:517551")).thenReturn(Optional.empty());
        when(vmsCrawlClient.fetchDetail("517551")).thenReturn(detail("517551", "모집중"));
        when(vmsRegionResolver.resolve(any())).thenReturn(7L);

        // 설정값(100)보다 큰 9999를 요청해도 상세조회는 정상 수행되며(1건뿐이라 상한에 걸리지 않음) 예외 없이 clamp된다.
        PostingSyncResult result = vmsPostingSyncService.syncRecentPostings(null, 9999);

        assertThat(result).isEqualTo(new PostingSyncResult(1, 1, 0, 0, 0));
    }

    @Test
    @DisplayName("override에 0 이하 값을 주면 예외가 발생한다")
    void syncRecentPostings_throws_whenOverrideNotPositive() {
        assertThatThrownBy(() -> vmsPostingSyncService.syncRecentPostings(0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private VmsPostingListItem listItem(String seq, String statusText) {
        return new VmsPostingListItem(
                seq, "제목-" + seq, "기관-" + seq, "2026-08-11 ~ 2026-08-13", statusText);
    }

    private VmsPostingDetail detail(String seq, String statusText) {
        return new VmsPostingDetail(
                seq,
                "제목-" + seq,
                statusText,
                "시설봉사 - 기타(시설봉사)",
                "2026-08-25 ~ 2026-08-28",
                "2026-08-07 ~ 2026-08-24",
                "기관-" + seq,
                "2명 / 1명",
                "장소-" + seq,
                "[경기] 경기도 시흥시",
                "담당자",
                "manager@example.com",
                "010-1234-5678",
                "본문 내용");
    }

    private Posting existingPosting(String extId) {
        return Posting.builder()
                .extId(extId)
                .title("기존 제목")
                .status(PostingStatus.RECRUITING)
                .activityDate(LocalDate.of(2026, 8, 11))
                .actStartDate(LocalDate.of(2026, 8, 11))
                .actEndDate(LocalDate.of(2026, 8, 13))
                .isActive(true)
                .category(PostingCategory.WELFARE)
                .source(PostingSource.VMS_CRAWL)
                .build();
    }
}
