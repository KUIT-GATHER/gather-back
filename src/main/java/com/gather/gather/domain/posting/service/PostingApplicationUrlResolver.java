package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingSource;
import org.springframework.stereotype.Component;

/**
 * 공고의 {@link PostingSource}에 따라 외부(1365/VMS) 신청 페이지 URL을 만든다.
 *
 * <p>기존에는 {@code PostingParticipationService.apply()} 내부에서만 1365 URL을 조합했지만, 신규 신청 플로우에서는
 * "공고 상세 조회" 시점에도 동일한 URL이 필요해(참여 생성 이전 단계) 별도 컴포넌트로 분리했다. 공고 상세 조회 / 참여 등록(과거 apply) 양쪽에서
 * 이 컴포넌트 하나만 재사용한다 — 프론트가 extId/vmsSeq 등을 받아 URL을 직접 조합하지 않도록, source별 URL 필드를 나누지 않고 단일
 * {@code applicationUrl} 필드로 응답한다(PostingResponse 참고).
 *
 * <h2>PR #171과의 통합 필요</h2>
 *
 * <p>열린 PR #171에서 {@code Posting.source} 기준 1365/VMS 신청 URL 분기 로직이 이미 작업 중이라고 안내받았다. 이 클래스는 그 로직을
 * 흡수하는 자리다. 머지 시:
 *
 * <ol>
 *   <li>PR #171의 URL 생성 로직(특히 VMS_CRAWL 분기)을 {@link #resolveVmsUrl(Posting)}로 옮겨준다.
 *   <li>이 클래스 안의 TODO/임시 구현을 지우고 실제 로직으로 교체한다.
 *   <li>기존 {@code VOLUNTEER_1365_APPLICATION_URL_PREFIX} 상수를 쓰던 {@code PostingParticipationService}는 이
 *       클래스를 주입받아 쓰도록 바뀌었으니 중복 정의가 남아있지 않은지 확인한다.
 * </ol>
 */
@Component
public class PostingApplicationUrlResolver {

    // 기존 PostingParticipationService에 있던 상수를 그대로 이전. extId가 progrmRegistNo(1365 프로그램 등록번호) 그
    // 자체다: "https://1365.go.kr/vols/P9210/partcptn/timeCptn.do?type=show&progrmRegistNo=" + extId
    private static final String VOLUNTEER_1365_APPLICATION_URL_PREFIX =
            "https://1365.go.kr/vols/P9210/partcptn/timeCptn.do?type=show&progrmRegistNo=";

    /**
     * source에 따라 외부 신청 페이지 URL을 반환한다. extId가 없어(예: 수동 등록 등 정합성 예외 케이스) 링크를 만들 수 없는 공고는 null을
     * 반환한다 — 프론트는 applicationUrl이 null이면 "신청하기" 버튼을 비활성화하거나 안내 문구를 노출해야 한다(Swagger에 명시 필요).
     */
    public String resolve(Posting posting) {
        if (posting.getExtId() == null || posting.getExtId().isBlank()) {
            return null;
        }
        return switch (posting.getSource()) {
            case API_1365 -> resolve1365Url(posting);
            case VMS_CRAWL -> resolveVmsUrl(posting);
        };
    }

    private String resolve1365Url(Posting posting) {
        return VOLUNTEER_1365_APPLICATION_URL_PREFIX + posting.getExtId();
    }

    /**
     * TODO(PR #171 병합 후 교체): VMS 신청 페이지 URL 생성. VmsPostingSyncService의 EXT_ID_PREFIX("vms:")를
     * 벗겨낸 seq 값으로 vms.or.kr 신청/상세 페이지 URL을 구성해야 한다. 정확한 경로는 PR #171의 기존 구현을 그대로 재사용할 것 — 여기서
     * 새로 추측한 URL 패턴을 운영에 반영하지 말 것.
     */
    private String resolveVmsUrl(Posting posting) {
        throw new UnsupportedOperationException(
                "VMS 신청 URL 생성 로직은 PR #171의 기존 구현으로 교체되어야 합니다. extId=" + posting.getExtId());
    }
}
