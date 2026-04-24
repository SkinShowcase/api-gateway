package com.skinsshowcase.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Глобальный фильтр для структурированного логирования запросов (метод, путь, routeId).
 * Не логирует тело и заголовки с чувствительными данными.
 */
@Component
public class RequestLoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingGlobalFilter.class);
    private static final String ROUTE_ATTR = "org.springframework.cloud.gateway.support.ServerWebExchangeUtils.gatewayPredicateRouteAttribute";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        logRequest(exchange);
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private void logRequest(ServerWebExchange exchange) {
        var request = exchange.getRequest();
        var method = request.getMethod().name();
        var path = request.getPath().value();
        var routeId = exchange.getAttribute(ROUTE_ATTR);
        log.info("gateway_request method={} path={} routeId={}", method, path, routeId);
    }
}
