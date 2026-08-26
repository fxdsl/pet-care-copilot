package com.petassistant.business.config;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.petassistant.business.client.SearchIndexClient;
import com.petassistant.business.data.dto.internal.SearchSourceDocument;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** OpenSearch 客户端必须沿用 Boot JSON 时间格式，不能输出小数秒时间戳。 */
class SearchConfigTest {

    @Test
    void shouldSerializeInstantAsIso8601ForOpenSearch() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            SearchProperties properties = properties("http://127.0.0.1:" + server.getAddress().getPort());
            ObjectMapper objectMapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            SearchIndexClient client = new SearchIndexClient(
                    new SearchConfig().searchRestClient(properties, objectMapper), objectMapper, properties
            );
            SearchSourceDocument document = new SearchSourceDocument(
                    "post-1", "POST", "幼猫喂养", "少量多餐", "alice", null, null,
                    "CAT", null, null, Instant.parse("2026-08-25T10:20:53.433863Z"),
                    "/app/community?post=post-1"
            );

            client.upsert(document, List.of(0.1D, 0.2D));

            assertThat(requestBody.get())
                    .contains("\"publishedAt\":\"2026-08-25T10:20:53.433863Z\"")
                    .doesNotContain("1787653253.433863");
        } finally {
            server.stop(0);
        }
    }

    private static SearchProperties properties(String endpoint) {
        return new SearchProperties(
                true, endpoint, "public-v1", 2,
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofMinutes(2),
                true, "search.events", "search.index", "search.index"
        );
    }
}
