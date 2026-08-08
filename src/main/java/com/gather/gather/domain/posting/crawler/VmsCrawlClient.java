package com.gather.gather.domain.posting.crawler;

import com.gather.gather.domain.posting.crawler.dto.VmsPostingDetail;
import com.gather.gather.domain.posting.crawler.dto.VmsPostingListItem;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * VMS(vms.or.kr) 목록/상세 페이지를 정적으로(Jsoup, JS 렌더링 없음) 가져온다. 요청 사이 지연을 두어 대상 서버 부하를 최소화한다 — 1365 클라이언트와
 * 달리 실패 시 자동 재시도는 하지 않는다(대상 사이트 부하를 늘리지 않기 위해 최소 시도만).
 */
@Component
@RequiredArgsConstructor
public class VmsCrawlClient {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final VmsCrawlProperties properties;

    public List<VmsPostingListItem> fetchList(int page, LocalDate actFrom, LocalDate actTo) {
        String url =
                properties.baseUrl()
                        + "/partspace/recruit.do?status=1&sttdte="
                        + actFrom.format(DATE_FORMAT)
                        + "&enddte="
                        + actTo.format(DATE_FORMAT)
                        + "&page="
                        + page;
        return VmsPostingHtmlParser.parseListPage(get(url));
    }

    public VmsPostingDetail fetchDetail(String seq) {
        String url = properties.baseUrl() + "/partspace/recruitView.do?seq=" + seq;
        return VmsPostingHtmlParser.parseDetailPage(get(url), seq);
    }

    public void sleepBetweenRequests() {
        if (properties.requestDelayMs() <= 0) {
            return;
        }
        try {
            Thread.sleep(properties.requestDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VmsCrawlException("크롤링 요청 지연 중 인터럽트됨", e);
        }
    }

    private Document get(String url) {
        try {
            return Jsoup.connect(url).userAgent(properties.userAgent()).timeout(10_000).get();
        } catch (IOException e) {
            throw new VmsCrawlException("VMS 페이지 요청 실패. url=" + url, e);
        }
    }
}
