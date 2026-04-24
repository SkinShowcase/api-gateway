package com.skinsshowcase.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Для запросов с {@code Authorization: Bearer} (кроме маршрутов на auth) проверяет
 * {@code GET /auth/session}: заблокированный или невалидный JWT не доходят до микросервисов.
 */
@Component
public class BlockedUserSessionGatewayFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SESSION_PATH = "/auth/session";
    private static final String JSON_BLOCKED = "{\"error\":\"Account blocked\"}";
    private static final String JSON_UPSTREAM = "{\"error\":\"Auth service unavailable\"}";

    private final WebClient authSessionWebClient;

    public BlockedUserSessionGatewayFilter(@Qualifier("authSessionCheckWebClient") WebClient authSessionCheckWebClient) {
        this.authSessionWebClient = authSessionCheckWebClient;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (shouldSkip(exchange)) {
            return chain.filter(exchange);
        }
        var authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return chain.filter(exchange);
        }
        return authSessionWebClient.get()
                .uri(SESSION_PATH)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .exchangeToMono(response -> dispatchAfterSession(response, exchange, chain))
                .onErrorResume(e -> writeJson(exchange, HttpStatus.SERVICE_UNAVAILABLE, JSON_UPSTREAM));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    private static boolean shouldSkip(ServerWebExchange exchange) {
        var method = exchange.getRequest().getMethod();
        if (method == HttpMethod.OPTIONS) {
            return true;
        }
        var path = exchange.getRequest().getPath().value();
        if (path == null) {
            return false;
        }
        return pathStartsWithAny(path, "/auth/", "/actuator", "/api-docs", "/v3/api-docs", "/swagger-ui", "/swagger-ui.html");
    }

    private static boolean pathStartsWithAny(String path, String... prefixes) {
        for (var p : prefixes) {
            if (path.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private static Mono<Void> dispatchAfterSession(
            ClientResponse response,
            ServerWebExchange exchange,
            GatewayFilterChain chain) {
        var code = response.statusCode().value();
        if (code == 204) {
            return response.releaseBody().then(chain.filter(exchange));
        }
        if (code == 401) {
            return response.releaseBody().then(writeUnauthorized(exchange));
        }
        if (code == 403) {
            return response.releaseBody().then(writeJson(exchange, HttpStatus.FORBIDDEN, JSON_BLOCKED));
        }
        return response.releaseBody().then(writeJson(exchange, HttpStatus.SERVICE_UNAVAILABLE, JSON_UPSTREAM));
    }

    private static Mono<Void> writeUnauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private static Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, String json) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        var buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
