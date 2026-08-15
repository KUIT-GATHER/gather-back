package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.crawler.VmsCrawlProperties;
import com.gather.gather.domain.posting.entity.Posting;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostingApplicationUrlResolver {

    private static final String VOLUNTEER_1365_APPLICATION_URL_PREFIX =
            "https://1365.go.kr/vols/P9210/partcptn/timeCptn.do?type=show&progrmRegistNo=";

    private final VmsCrawlProperties vmsCrawlProperties;

    public String resolve(Posting posting) {
        if (posting.getExtId() == null || posting.getExtId().isBlank()) {
            return null;
        }
        return switch (posting.getSource()) {
            case API_1365 -> VOLUNTEER_1365_APPLICATION_URL_PREFIX + posting.getExtId();
            case VMS_CRAWL ->
                    vmsCrawlProperties.baseUrl()
                            + "/partspace/recruitView.do?seq="
                            + vmsSeq(posting.getExtId());
        };
    }

    private String vmsSeq(String extId) {
        return extId.substring(VmsPostingSyncService.EXT_ID_PREFIX.length());
    }
}
