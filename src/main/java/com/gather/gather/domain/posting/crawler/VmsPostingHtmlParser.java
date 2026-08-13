package com.gather.gather.domain.posting.crawler;

import com.gather.gather.domain.posting.crawler.dto.VmsPostingDetail;
import com.gather.gather.domain.posting.crawler.dto.VmsPostingListItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * VMS 목록/상세 페이지의 순수 HTML 파싱만 담당한다. 네트워크 호출과 분리해두어, 테스트에서 저장된 fixture HTML을 {@code Jsoup.parse()}로
 * 읽어 실네트워크 없이 검증할 수 있다(1365 {@code VolunteerApiClientTest}가 XML fixture로 검증하는 것과 동일한 철학).
 */
final class VmsPostingHtmlParser {

    private static final Pattern SEQ_PATTERN = Pattern.compile("seq=(\\d+)");

    private VmsPostingHtmlParser() {}

    static List<VmsPostingListItem> parseListPage(Document doc) {
        List<VmsPostingListItem> items = new ArrayList<>();
        for (Element card : doc.select("li.card > a[href]")) {
            String seq = extractSeq(card.attr("href"));
            if (seq == null) {
                continue;
            }
            items.add(
                    new VmsPostingListItem(
                            seq,
                            textWithout(card.selectFirst("h3.title"), "span"),
                            textOf(card.selectFirst(".org")),
                            textWithout(card.selectFirst(".m-left"), "svg, h5"),
                            textOf(card.selectFirst(".state"))));
        }
        return items;
    }

    static VmsPostingDetail parseDetailPage(Document doc, String seq) {
        Map<String, String> kv = parseEvalKv(doc);
        Element contentEl = doc.selectFirst("div.board-content");
        Map<String, String> contact = parseContactBanner(doc);

        return new VmsPostingDetail(
                seq,
                textOf(doc.selectFirst("div.page-head h2.title")),
                textOf(doc.selectFirst("div.page-head span.badge")),
                kv.get("활동분야"),
                kv.get("활동기간"),
                kv.get("모집기간"),
                kv.get("봉사활동처"),
                kv.get("필요/신청 인원"),
                kv.get("봉사장소"),
                kv.get("봉사지역"),
                contact.get("담당자"),
                contact.get("이메일"),
                contact.get("연락처"),
                normalizeContent(contentEl));
    }

    private static String normalizeContent(Element contentEl) {
        if (contentEl == null) {
            return null;
        }
        return contentEl.wholeText().replace(' ', ' ').trim();
    }

    private static Map<String, String> parseEvalKv(Document doc) {
        Map<String, String> kv = new HashMap<>();
        Elements labels = doc.select("table.eval-kv th");
        Elements values = doc.select("table.eval-kv td");
        int size = Math.min(labels.size(), values.size());
        for (int i = 0; i < size; i++) {
            kv.put(labels.get(i).text().trim(), values.get(i).text().trim());
        }
        return kv;
    }

    private static Map<String, String> parseContactBanner(Document doc) {
        Map<String, String> contact = new HashMap<>();
        for (Element li : doc.select("div.contact-banner li")) {
            Element label = li.selectFirst("b");
            if (label == null) {
                continue;
            }
            Element clone = li.clone();
            clone.select("b").remove();
            contact.put(label.text().trim(), clone.text().replace(":", "").trim());
        }
        return contact;
    }

    private static String extractSeq(String href) {
        Matcher matcher = SEQ_PATTERN.matcher(href == null ? "" : href);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String textOf(Element el) {
        return el != null ? el.text().trim() : null;
    }

    private static String textWithout(Element el, String removeSelector) {
        if (el == null) {
            return null;
        }
        Element clone = el.clone();
        clone.select(removeSelector).remove();
        return clone.text().trim();
    }
}
