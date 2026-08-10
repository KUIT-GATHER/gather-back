package com.gather.gather.domain.posting.controller;

import com.gather.gather.domain.posting.service.PostingSyncResult;
import com.gather.gather.domain.posting.service.VmsPostingSyncService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * VMS 정적크롤링 수동 트리거(관리자용). 스케줄러(새벽 4시)를 기다리지 않고 즉시 동기화하기 위함.
 *
 * <p>{@code VmsPostingSyncScheduler}와 동일하게 {@code vms.crawl.scheduler-enabled}로 게이팅한다 —
 * robots.txt/이용약관 확인 전에는 스케줄러뿐 아니라 이 수동 엔드포인트로도 크롤링을 실행할 수 없어야 하기 때문이다. 경로가 {@code
 * /api/v1/admin/**}이라 {@code SecurityConfig}의 ADMIN_ONLY_PATHS로 이미 ADMIN role 전용이다.
 */
@Tag(name = "Admin - Posting VMS Sync", description = "VMS 정적크롤링 관리자 API (ADMIN 권한 필요)")
@RestController
@RequestMapping("/api/v1/admin/postings/vms-sync")
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "vms.crawl",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class VmsPostingSyncController {

    private final VmsPostingSyncService vmsPostingSyncService;

    @Operation(
            summary = "VMS 봉사공고 동기화 즉시 실행",
            description =
                    "매일 새벽 4시 배치(VmsPostingSyncScheduler)를 기다리지 않고 VMS 정적크롤링 동기화를 즉시 실행합니다. "
                            + "vms.crawl.scheduler-enabled=true인 환경에서만 활성화되며, ADMIN 권한이 없으면 403"
                            + " FORBIDDEN이 반환됩니다. maxPages/maxDetailLookups로 이번 실행 한정 소규모 테스트가"
                            + " 가능하며, 설정값(vms.crawl.*)보다 크게는 줄 수 없습니다(초과 요청 시 설정값으로 제한).")
    @PostMapping
    public ApiResponse<PostingSyncResult> sync(
            @Parameter(description = "이번 실행에서 조회할 목록 페이지 수 상한(생략 시 설정값 사용)")
                    @RequestParam(required = false)
                    Integer maxPages,
            @Parameter(description = "이번 실행에서 신규 공고 상세조회 횟수 상한(생략 시 설정값 사용)")
                    @RequestParam(required = false)
                    Integer maxDetailLookups) {
        return ApiResponse.success(
                vmsPostingSyncService.syncRecentPostings(maxPages, maxDetailLookups));
    }
}
