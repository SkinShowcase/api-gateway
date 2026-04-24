package com.skinsshowcase.gateway.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки TCP-прокси уведомлений: порт gateway и адрес сервиса messaging (TCP).
 */
@Component
@Validated
@Getter
public class GatewayTcpProperties {

    private final int port;
    private final String messagingHost;
    private final int messagingPort;

    public GatewayTcpProperties(
            @Min(1) @Max(65535) @Value("${gateway.notifications.tcp.port}") int port,
            @NotBlank @Value("${gateway.notifications.tcp.messaging-host}") String messagingHost,
            @Min(1) @Max(65535) @Value("${gateway.notifications.tcp.messaging-port}") int messagingPort) {
        this.port = port;
        this.messagingHost = messagingHost;
        this.messagingPort = messagingPort;
    }
}
