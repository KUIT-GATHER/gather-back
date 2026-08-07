package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.crawler.VmsCrawlClient;
import com.gather.gather.domain.posting.crawler.VmsCrawlProperties;
import com.gather.gather.domain.posting.crawler.dto.VmsPostingDetail;
import com.gather.gather.domain.posting.crawler.dto.VmsPostingListItem;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingSource;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.PostingRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * VMS(vms.or.kr) 정적 크롤링 동기화 배치.
 *
 * <p>1365와 아키텍처(신규/기존 분기, 실행당 상세조회 상한)는 같지만 근거는 다르다 — 1365는 일일 API 쿼터 보호가 목적이고, VMS는 크롤링 정중성(대상 서버
 * 부하 최소화)이 목적이다. 그래서 상한값도 1365의 값을 그대로 쓰지 않고 VMS 설정(vms.crawl.*)으로 별도 관리한다.
 *
 * <p>기존 공고 갱신은 목록카드 정보(제목/기관/상태/활동기간)만으로 처리한다 — VMS 목록카드는 1365 목록조회와 달리 category/actPlace/regionId를
 * 주지 않아(상세페이지 전용), 이 필드들은 매번 다시 상세조회하지 않고 최초 등록값을 유지한다. 대신 상태(모집중/모집완료)는 VMS가 계속 조회 가능한 값이라 1365처럼
 * "항상 active 유지"하지 않고 매 실행마다 실제 상태를 그대로 반영한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VmsPostingSyncService {

    private static final String EXT_ID_PREFIX = "vms:";

    /**
     * VMS 활동분야(acttype) 8종 → PostingCategory 6종. 1365의 CATEGORY_MAPPING(V14 마이그레이션과 동기화 의무 있음)과는
     * 완전히 독립된 상수다 — 절대 그쪽을 재사용하거나 확장하지 않는다.
     */
    private static final Map<String, PostingCategory> VMS_CATEGORY_MAPPING =
            Map.ofEntries(
                    Map.entry("시설봉사", PostingCategory.WELFARE),
                    Map.entry("재가봉사", PostingCategory.WELFARE),
                    Map.entry("전문봉사", PostingCategory.WELFARE),
                    Map.entry("지역사회봉사", PostingCategory.COMMUNITY),
                    Map.entry("금,물품봉사", PostingCategory.COMMUNITY),
                    Map.entry("해외봉사", PostingCategory.OVERSEAS),
                    Map.entry("헌혈", PostingCategory.WELFARE),
                    Map.entry("기타봉사", PostingCategory.COMMUNITY));

    private static final PostingCategory FALLBACK_CATEGORY = PostingCategory.COMMUNITY;

    private final VmsCrawlClient vmsCrawlClient;
    private final VmsCrawlProperties vmsCrawlProperties;
    private final PostingRepository postingRepository;
    private final VmsRegionResolver vmsRegionResolver;

    @Transactional
    public PostingSyncResult syncRecentPostings() {
        List<VmsPostingListItem> items = fetchListItems();
        int inserted = 0;
        int updated = 0;
        int failed = 0;
        int skipped = 0;
        int detailLookups = 0;

        for (VmsPostingListItem item : items) {
            try {
                boolean budgetExhausted =
                        detailLookups >= vmsCrawlProperties.maxDetailLookupsPerRun();
                switch (upsert(item, budgetExhausted)) {
                    case INSERTED -> {
                        inserted++;
                        detailLookups++;
                    }
                    case UPDATED -> updated++;
                    case SKIPPED -> skipped++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("VMS 봉사공고 동기화 실패. seq={}", item.seq(), e);
            }
        }

        PostingSyncResult result =
                new PostingSyncResult(items.size(), inserted, updated, failed, skipped);
        log.info(
                "VMS 봉사공고 동기화 완료. 대상={}, 신규={}, 갱신={}, 실패={}, 스킵={}",
                result.scanned(),
                result.inserted(),
                result.updated(),
                result.failed(),
                result.skipped());
        return result;
    }

    private List<VmsPostingListItem> fetchListItems() {
        LocalDate actFrom = LocalDate.now();
        LocalDate actTo = actFrom.plusDays(vmsCrawlProperties.actDaysAhead());

        List<VmsPostingListItem> result = new ArrayList<>();
        for (int page = 1; page <= vmsCrawlProperties.maxPages(); page++) {
            List<VmsPostingListItem> pageItems = vmsCrawlClient.fetchList(page, actFrom, actTo);
            if (pageItems.isEmpty()) {
                break;
            }
            result.addAll(pageItems);
            vmsCrawlClient.sleepBetweenRequests();
        }
        return result;
    }

    private enum UpsertOutcome {
        INSERTED,
        UPDATED,
        SKIPPED
    }

    private UpsertOutcome upsert(VmsPostingListItem item, boolean detailLookupBudgetExhausted) {
        String extId = EXT_ID_PREFIX + item.seq();
        return postingRepository
                .findByExtId(extId)
                .map(
                        existing -> {
                            updateExisting(existing, item);
                            return UpsertOutcome.UPDATED;
                        })
                .orElseGet(
                        () -> {
                            if (detailLookupBudgetExhausted) {
                                return UpsertOutcome.SKIPPED;
                            }
                            insertNew(item.seq());
                            return UpsertOutcome.INSERTED;
                        });
    }

    private void updateExisting(Posting posting, VmsPostingListItem item) {
        PostingStatus status = mapStatus(item.statusText());
        LocalDate[] actPeriod = parseDateRange(item.actPeriodText());
        posting.updateFromVmsSync(
                item.title(),
                status,
                item.org(),
                actPeriod[0],
                actPeriod[1],
                status == PostingStatus.RECRUITING);
    }

    private void insertNew(String seq) {
        VmsPostingDetail detail = vmsCrawlClient.fetchDetail(seq);
        vmsCrawlClient.sleepBetweenRequests();

        LocalDate[] actPeriod = parseDateRange(detail.actPeriodText());
        if (actPeriod[0] == null) {
            throw new IllegalStateException("activityDate(활동기간 시작일)가 없어 저장할 수 없습니다. seq=" + seq);
        }
        LocalDate[] noticePeriod = parseDateRange(detail.noticePeriodText());
        int[] counts = parseCounts(detail.countText());
        PostingStatus status = mapStatus(detail.statusText());

        Posting posting =
                Posting.builder()
                        .extId(EXT_ID_PREFIX + seq)
                        .title(detail.title())
                        .status(status)
                        .content(detail.content())
                        .recruitOrg(detail.org())
                        .registerOrg(detail.org())
                        .activityDate(actPeriod[0])
                        .actStartDate(actPeriod[0])
                        .actEndDate(actPeriod[1])
                        .noticeStartDate(noticePeriod[0])
                        .noticeEndDate(noticePeriod[1])
                        .recruitCount(counts[0])
                        .applicantCount(counts[1])
                        .isActive(status == PostingStatus.RECRUITING)
                        .actPlace(detail.actPlace())
                        .managerName(detail.managerName())
                        .managerTel(detail.managerTel())
                        .managerEmail(detail.managerEmail())
                        .regionId(vmsRegionResolver.resolve(detail.regionText()))
                        .category(resolveCategory(detail.categoryText()))
                        .source(PostingSource.VMS_CRAWL)
                        .build();

        postingRepository.save(posting);
    }

    /** VMS는 모집중/모집완료 2값뿐이라 1365의 코드추측형 매핑과 달리 단순 텍스트 매퍼로 충분하다. */
    private PostingStatus mapStatus(String statusText) {
        if (statusText == null) {
            return PostingStatus.RECRUITING;
        }
        return switch (statusText.trim()) {
            case "모집완료" -> PostingStatus.CLOSED;
            case "모집중" -> PostingStatus.RECRUITING;
            default -> {
                log.warn("알 수 없는 VMS 모집현황 값. value={}", statusText);
                yield PostingStatus.RECRUITING;
            }
        };
    }

    /** "시설봉사 - 기타(시설봉사)"처럼 대분류-소분류 텍스트에서 대분류만 추출해 매핑한다. */
    private PostingCategory resolveCategory(String categoryText) {
        if (categoryText != null && !categoryText.isBlank()) {
            String major = categoryText.split("-")[0].trim();
            PostingCategory matched = VMS_CATEGORY_MAPPING.get(major);
            if (matched != null) {
                return matched;
            }
            log.warn(
                    "VMS 활동분야에 매칭되는 카테고리가 없어 '{}'로 폴백합니다. value={}",
                    FALLBACK_CATEGORY,
                    categoryText);
        }
        return FALLBACK_CATEGORY;
    }

    /** "2026-08-25 ~ 2026-08-28" 또는 공백(" ~ ")을 파싱한다. 둘 다 비어있으면 {null, null}을 반환한다. */
    private LocalDate[] parseDateRange(String text) {
        LocalDate[] result = new LocalDate[2];
        if (text == null || text.isBlank()) {
            return result;
        }
        String[] parts = text.split("~");
        for (int i = 0; i < parts.length && i < 2; i++) {
            String trimmed = parts[i].trim();
            if (!trimmed.isEmpty()) {
                try {
                    result[i] = LocalDate.parse(trimmed);
                } catch (java.time.format.DateTimeParseException e) {
                    log.warn("VMS 날짜 파싱 실패. value={}", trimmed);
                }
            }
        }
        return result;
    }

    /** "필요/신청 인원" 셀 "2명 / 1명"을 [필요인원, 신청인원]으로 파싱한다. */
    private int[] parseCounts(String text) {
        int[] result = new int[2];
        if (text == null || text.isBlank()) {
            return result;
        }
        String[] parts = text.split("/");
        for (int i = 0; i < parts.length && i < 2; i++) {
            String digits = parts[i].replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                result[i] = Integer.parseInt(digits);
            }
        }
        return result;
    }
}
