package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.client.VolunteerApiClient;
import com.gather.gather.domain.posting.client.dto.VolunteerApiItemDto;
import com.gather.gather.domain.posting.client.dto.VolunteerApiSearchCondition;
import com.gather.gather.domain.posting.client.dto.VolunteerApiSearchItemDto;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingStatus;
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
 * <p>알고리즘은 임시안이다({@code noticeBgnde} 최근 N일 윈도우로 검색 → 신규는 상세조회로 insert, 기존은 목록 필드만
 * update). 정확도/쿼터 트레이드오프 재검토가 필요하다 — {@code docs/devplan.md} "추후 검토 필요 항목" 참고.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostingSyncService {

    // TODO(연석): category 도메인 완성되면 실제 매칭 로직으로 교체. docs/devplan.md 참고.
    private static final Long DEFAULT_CATEGORY_ID = 1L;
    private static final int SEARCH_PAGE_SIZE = 100;
    private static final int NOTICE_WINDOW_DAYS = 3;
    private static final DateTimeFormatter API_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final VolunteerApiClient volunteerApiClient;
    private final PostingRepository postingRepository;
    private final RegionRepository regionRepository;

    @Transactional
    public void syncRecentPostings() {
        List<VolunteerApiSearchItemDto> items = fetchRecentItems();
        int inserted = 0;
        int updated = 0;
        int failed = 0;

        for (VolunteerApiSearchItemDto item : items) {
            try {
                if (upsert(item)) {
                    inserted++;
                } else {
                    updated++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("봉사공고 동기화 실패. progrmRegistNo={}", item.progrmRegistNo(), e);
            }
        }

        log.info("봉사공고 동기화 완료. 대상={}, 신규={}, 갱신={}, 실패={}", items.size(), inserted, updated, failed);
    }

    private List<VolunteerApiSearchItemDto> fetchRecentItems() {
        String noticeBgnde = LocalDate.now().minusDays(NOTICE_WINDOW_DAYS).format(API_DATE_FORMAT);
        String noticeEndde = LocalDate.now().format(API_DATE_FORMAT);
        VolunteerApiSearchCondition condition =
                new VolunteerApiSearchCondition(
                        null, null, null, null, null, null, null, null, noticeBgnde, noticeEndde,
                        null, null, null, null, null, null);

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

    /** @return 신규 insert면 true, 기존 update면 false */
    private boolean upsert(VolunteerApiSearchItemDto item) {
        return postingRepository
                .findByExtId(item.progrmRegistNo())
                .map(
                        existing -> {
                            updateExisting(existing, item);
                            return false;
                        })
                .orElseGet(
                        () -> {
                            insertNew(item.progrmRegistNo());
                            return true;
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
                resolveRegionId(item.sidoCd(), item.gugunCd()));
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
                        .managerName(detail.nanmmbyNmAdmn())
                        .managerTel(detail.telno())
                        .managerFax(detail.fxnum())
                        .managerEmail(detail.email())
                        .latitude(parseCoordinate(detail.areaLalo1(), 0))
                        .longitude(parseCoordinate(detail.areaLalo1(), 1))
                        .regionId(resolveRegionId(detail.sidoCd(), detail.gugunCd()))
                        .categoryId(DEFAULT_CATEGORY_ID)
                        .build();

        postingRepository.save(posting);
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

    /**
     * progrmSttusSe(모집상태코드) → {@link PostingStatus}.
     *
     * <p>API 스펙 문서에 코드별 의미가 명시되어 있지 않아 1365 일반 관례(1=모집예정, 2=모집중, 3=모집마감/완료)로
     * 추정한 값이다. 실제 응답으로 검증 전까지는 근사치 — Day5 수동 검증 때 재확인 필요(devplan.md 참고).
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
