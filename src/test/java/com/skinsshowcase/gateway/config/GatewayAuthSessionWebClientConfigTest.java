package com.skinsshowcase.gateway.config;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAuthSessionWebClientConfigTest {

    @Test
    void authSessionCheckWebClient_trimsTrailingSlashesAndCallsBaseUrl() throws Exception {
        try (var mockAuth = new MockWebServer()) {
            mockAuth.start();
            mockAuth.enqueue(new MockResponse().setBody("{\"ok\":true}"));

            var rawBase = mockAuth.url("/").toString();
            var props = new GatewaySessionCheckProperties(rawBase + "///", 5_000, 5_000);
            WebClient client = new GatewayAuthSessionWebClientConfig().authSessionCheckWebClient(props);

            String body = client.get()
                    .uri("/internal/session-check")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            assertThat(body).isEqualTo("{\"ok\":true}");
            var req = mockAuth.takeRequest();
            assertThat(req.getPath()).isEqualTo("/internal/session-check");
        }
    }
}
