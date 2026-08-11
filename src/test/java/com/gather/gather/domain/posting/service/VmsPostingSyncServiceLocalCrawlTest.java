package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.posting.crawler.VmsCrawlClient;
import com.gather.gather.domain.posting.crawler.VmsCrawlProperties;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * robots.txt/이용약관 미확인 상태라 실제 vms.or.kr에는 절대 요청하지 않는다. 대신 기존 fixture HTML을 로컬 loopback(HttpServer)으로
 * 서빙해, 실제 {@link VmsCrawlClient}+파서를 통해 "이번 실행 한정 소규모 테스트" (maxDetailLookups override)가 대상 서버 요청
 * 횟수를 실제로 제한하는지 end-to-end로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class VmsPostingSyncServiceLocalCrawlTest {

    private static final int ITEMS_PER_PAGE = 12;
    private static final int PAGES = 9; // 9 * 12 = 108 > 100, 상한 검증을 위해 100을 넘도록 설정
    private static final int DETAIL_LOOKUP_LIMIT = 100;

    private static HttpServer server;
    private static String baseUrl;

    @Mock private PostingRepository postingRepository;
    @Mock private VmsRegionResolver vmsRegionResolver;
    @Mock private PlatformTransactionManager transactionManager;

    @BeforeAll
    static void startLocalFixtureServer() throws IOException {
        String listPageHtml = readFixture("/vms/list_page.html");
        String detailPageHtml = readFixture("/vms/detail_recruiting.html");

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/partspace/recruit.do", exchange -> respond(exchange, listPageHtml));
        server.createContext(
                "/partspace/recruitView.do", exchange -> respond(exchange, detailPageHtml));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopLocalFixtureServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("로컬 fixture 서버 기준 maxDetailLookups=100이면 상세조회가 정확히 100건에서 멈춘다")
    void syncRecentPostings_stopsDetailLookups_at100_againstLocalFixtureServer() {
        VmsCrawlProperties properties =
                new VmsCrawlProperties(baseUrl, "KUIT-Gather-Bot-Test/1.0", PAGES, 0, 30, 1000);
        VmsPostingSyncService service =
                new VmsPostingSyncService(
                        new VmsCrawlClient(properties),
                        properties,
                        postingRepository,
                        vmsRegionResolver,
                        transactionManager);
        when(postingRepository.findByExtId(any())).thenReturn(Optional.empty());
        when(vmsRegionResolver.resolve(any())).thenReturn(1L);

        PostingSyncResult result = service.syncRecentPostings(null, DETAIL_LOOKUP_LIMIT);

        assertThat(result.scanned()).isEqualTo(PAGES * ITEMS_PER_PAGE);
        assertThat(result.inserted()).isEqualTo(DETAIL_LOOKUP_LIMIT);
        assertThat(result.skipped()).isEqualTo(PAGES * ITEMS_PER_PAGE - DETAIL_LOOKUP_LIMIT);
        assertThat(result.updated()).isZero();
        assertThat(result.failed()).isZero();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readFixture(String classpathResource) throws IOException {
        try (InputStream in =
                VmsPostingSyncServiceLocalCrawlTest.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IOException("fixture를 찾을 수 없습니다: " + classpathResource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
