package com.gather.gather.domain.posting.controller;

import com.gather.gather.domain.posting.service.PostingSyncResult;
import com.gather.gather.domain.posting.service.PostingSyncService;
import com.gather.gather.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 수동 검증용 임시 엔드포인트(devplan.md Day5). 스케줄러(새벽 3시)를 기다리지 않고 동기화를 즉시 트리거하기
 * 위함. Day6 이후 정식 {@code PostingController} 작업 시 제거하거나 관리자 전용으로 재설계할 것.
 *
 * <p>{@code posting.sync.manual-endpoint-enabled=true}가 명시된 환경(로컬)에서만 빈이 등록되어, 다른
 * 환경에 실수로 노출되지 않는다.
 */
@RestController
@RequestMapping("/api/v1/postings")
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "posting.sync",
        name = "manual-endpoint-enabled",
        havingValue = "true")
public class PostingSyncController {

    private final PostingSyncService postingSyncService;

    @PostMapping("/sync")
    public ApiResponse<PostingSyncResult> sync() {
        return ApiResponse.success(postingSyncService.syncRecentPostings());
    }
}
