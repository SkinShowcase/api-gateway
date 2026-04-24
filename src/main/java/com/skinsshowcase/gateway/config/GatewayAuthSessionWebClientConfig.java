package com.skinsshowcase.gateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class GatewayAuthSessionWebClientConfig {

    @Bean(name = "authSessionCheckWebClient")
    public WebClient authSessionCheckWebClient(GatewaySessionCheckProperties properties) {
        var base = properties.getAuthBaseUrl().replaceAll("/+$", "");
        var httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return WebClient.builder()
                .baseUrl(base)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
