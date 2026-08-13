package com.gather.gather.domain.posting.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.posting.crawler.dto.VmsPostingDetail;
import com.gather.gather.domain.posting.crawler.dto.VmsPostingListItem;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 실제로 저장한 VMS 목록/상세 페이지 HTML(2026-08-07 확보)을 fixture로 파싱을 검증한다. 실네트워크 호출 없음. */
class VmsPostingHtmlParserTest {

    @Test
    @DisplayName("목록페이지 카드에서 seq/제목/기관/활동기간/상태를 파싱한다")
    void parseListPage_extractsCards() {
        Document doc = loadFixture("/vms/list_page.html");

        List<VmsPostingListItem> items = VmsPostingHtmlParser.parseListPage(doc);

        assertThat(items).isNotEmpty();
        VmsPostingListItem first = items.get(0);
        assertThat(first.seq()).isEqualTo("517531");
        assertThat(first.title()).contains("범안종합사회복지관").contains("급식서비스");
        assertThat(first.org()).isEqualTo("범안종합사회복지관");
        assertThat(first.actPeriodText()).contains("2026-08-11").contains("2026-08-13");
        assertThat(first.statusText()).isEqualTo("모집중");
    }

    @Test
    @DisplayName("모집중 상세페이지의 활동기간/모집기간/담당자/본문을 라벨 기준으로 파싱한다")
    void parseDetailPage_recruiting() {
        Document doc = loadFixture("/vms/detail_recruiting.html");

        VmsPostingDetail detail = VmsPostingHtmlParser.parseDetailPage(doc, "517551");

        assertThat(detail.seq()).isEqualTo("517551");
        assertThat(detail.title()).contains("대야어르신작은복지관");
        assertThat(detail.statusText()).isEqualTo("모집중");
        assertThat(detail.categoryText()).contains("시설봉사");
        assertThat(detail.actPeriodText()).isEqualTo("2026-08-25 ~ 2026-08-28");
        assertThat(detail.noticePeriodText()).isEqualTo("2026-08-07 ~ 2026-08-24");
        assertThat(detail.org()).isEqualTo("시흥시북부노인복지관");
        assertThat(detail.countText()).isEqualTo("2명 / 1명");
        assertThat(detail.actPlace()).isEqualTo("대야어르신작은복지관");
        assertThat(detail.regionText()).contains("경기도 시흥시");
        assertThat(detail.managerName()).isEqualTo("안소연");
        assertThat(detail.managerEmail()).isEqualTo("dksthdus3103@naver.com");
        assertThat(detail.managerTel()).isEqualTo("010-5239-9387");
        assertThat(detail.content()).contains("자원봉사자를 모집합니다");
    }

    @Test
    @DisplayName("모집기간이 비어있는(비대면) 상세페이지는 noticePeriodText가 null로 파싱된다")
    void parseDetailPage_online_blankNoticePeriod() {
        Document doc = loadFixture("/vms/detail_online.html");

        VmsPostingDetail detail = VmsPostingHtmlParser.parseDetailPage(doc, "seq");

        assertThat(detail.actPeriodText()).isEqualTo("2026-08-01 ~ 2026-08-31");
        assertThat(detail.org()).isEqualTo("하상장애인복지관");
        assertThat(detail.actPlace()).isEqualTo("재택");
    }

    @Test
    @DisplayName("정기 활동주기 상세페이지도 동일한 라벨 구조로 파싱된다")
    void parseDetailPage_schedule() {
        Document doc = loadFixture("/vms/detail_schedule.html");

        VmsPostingDetail detail = VmsPostingHtmlParser.parseDetailPage(doc, "seq");

        assertThat(detail.actPeriodText()).isEqualTo("2026-08-07 ~ 2026-12-31");
        assertThat(detail.noticePeriodText()).isEqualTo("2026-08-03 ~ 2026-08-31");
        assertThat(detail.org()).isEqualTo("울산중구종합사회복지관");
        assertThat(detail.actPlace()).isEqualTo("중구종합사회복지관(분관)");
    }

    private Document loadFixture(String classpathResource) {
        try (InputStream in = getClass().getResourceAsStream(classpathResource)) {
            return Jsoup.parse(in, "UTF-8", "https://www.vms.or.kr");
        } catch (IOException e) {
            throw new IllegalStateException("fixture 로드 실패: " + classpathResource, e);
        }
    }
}
