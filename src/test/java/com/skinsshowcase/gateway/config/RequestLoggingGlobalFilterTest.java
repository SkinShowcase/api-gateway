package com.skinsshowcase.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestLoggingGlobalFilterTest {

    @Test
    void delegatesToChain() {
        var filter = new RequestLoggingGlobalFilter();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/items").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void orderIsHighestPrecedence() {
        assertThat(new RequestLoggingGlobalFilter().getOrder())
                .isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    }
}
