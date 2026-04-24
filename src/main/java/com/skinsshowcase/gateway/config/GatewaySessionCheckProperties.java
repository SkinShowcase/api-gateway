package com.skinsshowcase.gateway.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Проверка сессии через auth ({@code GET /auth/session}) для запросов с JWT.
 */
@Component
@Validated
@Getter
public class GatewaySessionCheckProperties {

    private final String authBaseUrl;
    private final long connectTimeoutMs;
    private final long readTimeoutMs;

    public GatewaySessionCheckProperties(
            @NotBlank @Value("${gateway.session-check.auth-base-url}") String authBaseUrl,
            @Positive @Value("${gateway.session-check.connect-timeout-ms}") long connectTimeoutMs,
            @Positive @Value("${gateway.session-check.read-timeout-ms}") long readTimeoutMs) {
        this.authBaseUrl = authBaseUrl;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }
}
