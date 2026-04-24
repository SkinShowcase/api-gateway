package com.skinsshowcase.gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Возвращает все ошибки gateway (404, 502, 503 и т.д.) в формате JSON.
 * Приоритет выше дефолтного обработчика, чтобы фронт всегда получал application/json.
 */
@Component
@Order(-2)
public class JsonErrorWebExceptionHandler implements org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(JsonErrorWebExceptionHandler.class);

    /** Ключ атрибута исключения в ServerWebExchange (как в DefaultErrorAttributes). */
    private static final String ERROR_ATTRIBUTE =
            "org.springframework.boot.web.reactive.error.DefaultErrorAttributes.ERROR";

    private final ErrorAttributes errorAttributes;
    private final ObjectMapper objectMapper;

    public JsonErrorWebExceptionHandler(GatewayErrorAttributes errorAttributes, ObjectMapper objectMapper) {
        this.errorAttributes = errorAttributes;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        exchange.getAttributes().putIfAbsent(ERROR_ATTRIBUTE, ex);
        var request = ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders());
        var attrs = errorAttributes.getErrorAttributes(request, ErrorAttributeOptions.defaults());
        var status = attrs.get("status") instanceof Integer i ? i : 500;
        var statusCode = resolveHttpStatus(status);
        var response = exchange.getResponse();
        response.setStatusCode(statusCode);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var jsonBytes = writeJson(attrs);
        if (jsonBytes == null) {
            return Mono.error(ex);
        }
        DataBuffer buffer = response.bufferFactory().wrap(jsonBytes);
        return response.writeWith(Mono.just(buffer));
    }

    private static HttpStatus resolveHttpStatus(int status) {
        try {
            return HttpStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private byte[] writeJson(Map<String, Object> attrs) {
        try {
            return objectMapper.writeValueAsString(attrs).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize error to JSON: {}", e.getMessage());
            return new byte[0];
        }
    }
}
