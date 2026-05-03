package com.skinsshowcase.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class JsonErrorWebExceptionHandlerTest {

    private JsonErrorWebExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new JsonErrorWebExceptionHandler(new GatewayErrorAttributes(), new ObjectMapper());
    }

    @Test
    void writesJsonErrorBody() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/r").build());
        var ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "missing");

        StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
    }
}
