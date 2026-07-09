package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.category.repository.CategoryRepository;
import com.gather.gather.domain.posting.client.VolunteerApiClient;
import com.gather.gather.domain.posting.client.dto.VolunteerApiItemDto;
import com.gather.gather.domain.posting.client.dto.VolunteerApiSearchCondition;
import com.gather.gather.domain.posting.client.dto.VolunteerApiSearchItemDto;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingLocation;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.PostingLocationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1365 자원봉사포털 동기화 배치.
 *
 * <p>노출되는 전체 목록을 매 실행마다 날짜 필터 없이 페이지네이션한다({@code noticeBgnde} 등 하한을 걸지 않음). 실측 검증(2026-07-06) 결과, 이
 * API는 모집기간이 지난 공고를 다음날부터 어떤 오퍼레이션으로도 완전히 조회할 수 없게 만든다 — 그래서 날짜 하한을 걸면 오래전에 열려 여전히 모집중인 장기 공고를 놓치게
 * 된다({@code docs/devplan.md} 섹션 8.1 참고).
 *
 * <p>"마감 2주 이내 공고 유지"는 API를 통해 재조회하는 방식이 아니라, {@code is_active}를 항상 {@code true}로 유지하고
 * 삭제/soft-delete 로직을 두지 않는 정책으로 충족한다({@code docs/devplan.md} 섹션 7.2) — 마감 이후 목록에서 사라진 공고는 그날 이후로
 * 갱신되지 않을 뿐, DB 행은 마지막으로 동기화된 상태 그대로 남는다.
 *
 * <p>신규 공고(상세조회 필요)가 실행당 {@link #MAX_DETAIL_LOOKUPS_PER_RUN}건을 넘으면 남은 건은 처리를 멈추고 다음 실행으로 넘긴다(개발계정
 * 1,000회/일 쿼터 보호). 여전히 모집중이면 다음 실행의 전체 목록에 다시 나타나므로 데이터 유실은 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostingSyncService {

    /** srvcClCode(봉사분야명 텍스트)가 Category.name 어디에도 매칭되지 않을 때 사용하는 폴백 분야. V5 시드데이터에 존재. */
    private static final String FALLBACK_CATEGORY_NAME = "기타";

    private static final int SEARCH_PAGE_SIZE = 100;
    private static final int MAX_DETAIL_LOOKUPS_PER_RUN = 800;
    private static final DateTimeFormatter API_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final VolunteerApiClient volunteerApiClient;
    private final PostingRepository postingRepository;
    private final RegionRepository regionRepository;
    private final PostingLocationRepository postingLocationRepository;
    private final CategoryRepository categoryRepository;
    private final DongResolver dongResolver;

    @Transactional
    public PostingSyncResult syncRecentPostings() {
        List<VolunteerApiSearchItemDto> items = fetchActivePostings();
        int inserted = 0;
        int updated = 0;
        int failed = 0;
        int skipped = 0;
        boolean budgetWarningLogged = false;

        for (VolunteerApiSearchItemDto item : items) {
            try {
                boolean budgetExhausted = inserted >= MAX_DETAIL_LOOKUPS_PER_RUN;
                if (budgetExhausted && !budgetWarningLogged) {
                    log.warn(
                            "상세조회 쿼터 보호 상한({}건)에 도달해 이후 신규 공고는 다음 실행으로 넘깁니다.",
                            MAX_DETAIL_LOOKUPS_PER_RUN);
                    budgetWarningLogged = true;
                }
                switch (upsert(item, budgetExhausted)) {
                    case INSERTED -> inserted++;
                    case UPDATED -> updated++;
                    case SKIPPED -> skipped++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("봉사공고 동기화 실패. progrmRegistNo={}", item.progrmRegistNo(), e);
            }
        }

        PostingSyncResult result =
                new PostingSyncResult(items.size(), inserted, updated, failed, skipped);
        log.info(
                "봉사공고 동기화 완료. 대상={}, 신규={}, 갱신={}, 실패={}, 쿼터보호스킵={}",
                result.scanned(),
                result.inserted(),
                result.updated(),
                result.failed(),
                result.skipped());
        return result;
    }

    private List<VolunteerApiSearchItemDto> fetchActivePostings() {
        VolunteerApiSearchCondition condition =
                new VolunteerApiSearchCondition(
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null);

        List<VolunteerApiSearchItemDto> result = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            List<VolunteerApiSearchItemDto> page =
                    volunteerApiClient.searchList(condition, pageNo, SEARCH_PAGE_SIZE);
            result.addAll(page);
            if (page.size() < SEARCH_PAGE_SIZE) {
                break;
            }
            pageNo++;
        }
        return result;
    }

    private enum UpsertOutcome {
        INSERTED,
        UPDATED,
        SKIPPED
    }

    private UpsertOutcome upsert(
            VolunteerApiSearchItemDto item, boolean detailLookupBudgetExhausted) {
        return postingRepository
                .findByExtId(item.progrmRegistNo())
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
                            insertNew(item.progrmRegistNo());
                            return UpsertOutcome.INSERTED;
                        });
    }

    private void updateExisting(Posting posting, VolunteerApiSearchItemDto item) {
        posting.updateFromSync(
                item.progrmSj(),
                mapStatus(item.progrmSttusSe()),
                item.nanmmbyNm(),
                parseDate(item.progrmBgnde()),
                parseDate(item.progrmEndde()),
                parseDate(item.noticeBgnde()),
                parseDate(item.noticeEndde()),
                item.actPlace(),
                parseYn(item.adultPosblAt()),
                parseYn(item.yngbgsPosblAt()),
                resolveRegionIdForUpdate(posting.getRegionId(), item.sidoCd(), item.gugunCd()),
                resolveCategoryId(item.srvcClCode()));
    }

    /**
     * 목록조회 응답에는 areaAddress/postAdres가 없어 actPlace만으로는 동 재추정 성공률이 낮다(실측 1% 수준). 매 동기화마다 모든 기존 공고에
     * 대해 비용만 크고 효과는 적은 재추정을 시도하는 대신, insertNew에서 이미 확정된 동 단위 regionId를 구 단위로 되돌리지만 않는다(폴백: 기존 동 유지
     * > 구 매칭).
     */
    private Long resolveRegionIdForUpdate(Long currentRegionId, String sidoCd, String gugunCd) {
        Long guId = resolveRegionId(sidoCd, gugunCd);
        if (guId == null) {
            return currentRegionId;
        }
        if (dongResolver.isDongOf(currentRegionId, guId)) {
            return currentRegionId;
        }
        return guId;
    }

    private void insertNew(String progrmRegistNo) {
        VolunteerApiItemDto detail = volunteerApiClient.getItem(progrmRegistNo);

        LocalDate activityDate = parseDate(detail.progrmBgnde());
        if (activityDate == null) {
            throw new IllegalStateException(
                    "activityDate(progrmBgnde)가 없어 저장할 수 없습니다. progrmRegistNo=" + progrmRegistNo);
        }

        Posting posting =
                Posting.builder()
                        .extId(detail.progrmRegistNo())
                        .title(detail.progrmSj())
                        .status(mapStatus(detail.progrmSttusSe()))
                        .content(detail.progrmCn())
                        .recruitOrg(detail.mnnstNm())
                        .registerOrg(detail.nanmmbyNm())
                        .activityDate(activityDate)
                        .actStartDate(activityDate)
                        .actEndDate(parseDate(detail.progrmEndde()))
                        .actStartTime(detail.actBeginTm())
                        .actEndTime(detail.actEndTm())
                        .noticeStartDate(parseDate(detail.noticeBgnde()))
                        .noticeEndDate(parseDate(detail.noticeEndde()))
                        .actWkdy(detail.actWkdy())
                        .recruitCount(parseInt(detail.rcritNmpr()))
                        .applicantCount(parseInt(detail.appTotal()))
                        .isAdult(parseYn(detail.adultPosblAt()))
                        .isTeen(parseYn(detail.yngbgsPosblAt()))
                        .isGroup(parseYn(detail.grpPosblAt()))
                        .isActive(true)
                        .actPlace(detail.actPlace())
                        // areaAddress1=봉사장소주소1(공식 확인, docs/devplan.md §9). 표본조사상 일부(2/20)
                        // "수요처 지도" placeholder가 섞여 있으나 기존 파싱 관행대로 별도 검증 없이 그대로 저장.
                        .postAddress(detail.areaAddress1())
                        .managerName(detail.nanmmbyNmAdmn())
                        .managerTel(detail.telno())
                        .managerFax(detail.fxnum())
                        .managerEmail(detail.email())
                        .managerAddress(detail.postAdres())
                        .latitude(parseCoordinate(detail.areaLalo1(), 0))
                        .longitude(parseCoordinate(detail.areaLalo1(), 1))
                        .regionId(
                                resolveRegionIdWithDong(
                                        resolveRegionId(detail.sidoCd(), detail.gugunCd()),
                                        detail.areaAddress1(),
                                        detail.postAdres(),
                                        detail.actPlace()))
                        .categoryId(resolveCategoryId(detail.srvcClCode()))
                        .build();

        Posting saved = postingRepository.save(posting);
        saveAdditionalLocation(saved, 2, detail.areaAddress2(), detail.areaLalo2());
        saveAdditionalLocation(saved, 3, detail.areaAddress3(), detail.areaLalo3());
    }

    /**
     * 2·3번째 활동장소는 areaAddress{n}이 채워진 경우에만 생성한다(devplan.md 섹션 8.2). 목록조회 응답엔 이 필드들이 없어
     * updateFromSync() 경로는 건드리지 않는다.
     */
    private void saveAdditionalLocation(
            Posting posting, int locationSeq, String address, String areaLalo) {
        if (address == null || address.isBlank()) {
            return;
        }
        postingLocationRepository.save(
                PostingLocation.create(
                        posting.getId(),
                        locationSeq,
                        address,
                        parseCoordinate(areaLalo, 0),
                        parseCoordinate(areaLalo, 1)));
    }

    private Long resolveRegionId(String sidoCd, String gugunCd) {
        if (gugunCd != null && !gugunCd.isBlank()) {
            var byGugun = regionRepository.findByCode(gugunCd);
            if (byGugun.isPresent()) {
                return byGugun.get().getId();
            }
        }
        if (sidoCd != null && !sidoCd.isBlank()) {
            return regionRepository.findByCode(sidoCd).map(region -> region.getId()).orElse(null);
        }
        return null;
    }

    /** 구/시 단위 regionId를 확정한 뒤, 주소 텍스트에서 동까지 찾아지면 더 구체적인 동 단위로 승격한다. */
    private Long resolveRegionIdWithDong(
            Long guRegionId, String areaAddress1, String postAdres, String actPlace) {
        if (guRegionId == null) {
            return null;
        }
        Long dongId = dongResolver.resolve(guRegionId, areaAddress1, postAdres, actPlace);
        return dongId != null ? dongId : guRegionId;
    }

    /**
     * srvcClCode는 코드가 아니라 분야명 텍스트로 응답된다(예: "생활편의", devplan.md §6 참고). {@code Category.name}과 텍스트
     * 매칭하고, 매칭 실패 시 {@link #FALLBACK_CATEGORY_NAME}으로 폴백한다({@code categoryId}가 NOT NULL이라 폴백 없이는 저장
     * 자체가 불가능).
     */
    private Long resolveCategoryId(String srvcClCode) {
        if (srvcClCode != null && !srvcClCode.isBlank()) {
            var matched = categoryRepository.findByName(srvcClCode.trim());
            if (matched.isPresent()) {
                return matched.get().getId();
            }
            log.warn(
                    "srvcClCode에 매칭되는 Category가 없어 '{}'로 폴백합니다. value={}",
                    FALLBACK_CATEGORY_NAME,
                    srvcClCode);
        }
        return categoryRepository
                .findByName(FALLBACK_CATEGORY_NAME)
                .map(category -> category.getId())
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "폴백 카테고리('"
                                                + FALLBACK_CATEGORY_NAME
                                                + "')가 시드되어 있지 않습니다."));
    }

    /**
     * progrmSttusSe(모집상태코드) → {@link PostingStatus}.
     *
     * <p>API 스펙 문서에 코드별 의미가 명시되어 있지 않아 1365 일반 관례(1=모집예정, 2=모집중, 3=모집마감/완료)로 추정한 값이다. 실제 응답으로 검증
     * 전까지는 근사치 — Day5 수동 검증 때 재확인 필요(devplan.md 참고).
     */
    private PostingStatus mapStatus(String progrmSttusSe) {
        if (progrmSttusSe == null) {
            return PostingStatus.RECRUITING;
        }
        return switch (progrmSttusSe.trim()) {
            case "1", "2" -> PostingStatus.RECRUITING;
            case "3" -> PostingStatus.CLOSED;
            default -> {
                log.warn("알 수 없는 progrmSttusSe 값. value={}", progrmSttusSe);
                yield PostingStatus.RECRUITING;
            }
        };
    }

    private LocalDate parseDate(String yyyyMMdd) {
        if (yyyyMMdd == null || yyyyMMdd.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(yyyyMMdd, API_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            log.warn("날짜 파싱 실패. value={}", yyyyMMdd);
            return null;
        }
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            log.warn("숫자 파싱 실패. value={}", value);
            return null;
        }
    }

    private Boolean parseYn(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "Y".equalsIgnoreCase(value.trim());
    }

    /** areaLalo1은 "위도,경도" 콤마구분 문자열. index 0=위도, 1=경도. */
    private BigDecimal parseCoordinate(String areaLalo, int index) {
        if (areaLalo == null || areaLalo.isBlank()) {
            return null;
        }
        String[] parts = areaLalo.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new BigDecimal(parts[index].trim());
        } catch (NumberFormatException e) {
            log.warn("좌표 파싱 실패. value={}", areaLalo);
            return null;
        }
    }
}
