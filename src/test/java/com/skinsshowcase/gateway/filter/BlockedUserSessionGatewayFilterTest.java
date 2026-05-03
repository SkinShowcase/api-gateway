package com.skinsshowcase.gateway.filter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockedUserSessionGatewayFilterTest {

    private MockWebServer mockWebServer;
    private BlockedUserSessionGatewayFilter filter;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        var base = mockWebServer.url("/").toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        filter = new BlockedUserSessionGatewayFilter(WebClient.builder().baseUrl(base).build());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void skipsAuthRoutes() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer t")
                        .build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        assertThat(mockWebServer.getRequestCount()).isZero();
    }

    @Test
    void skipsOptions() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/v1/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer t")
                        .build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        assertThat(mockWebServer.getRequestCount()).isZero();
    }

    @Test
    void skipsWhenNoBearer() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/items").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        assertThat(mockWebServer.getRequestCount()).isZero();
    }

    @Test
    void callsSessionAndContinuesOn204() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ok-token")
                        .build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).isEqualTo("/auth/session");
        assertThat(req.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer ok-token");
    }

    @Test
    void returns401WhenSessionReturns401() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(401));
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/chats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bad")
                        .build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void returns403JsonWhenSessionReturns403() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(403));
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/chats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer blocked")
                        .build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void orderIsDefined() {
        assertThat(filter.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 5);
    }
}
