package com.skinsshowcase.gateway.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySessionCheckPropertiesTest {

    @Test
    void holdsConfiguredValues() {
        var props = new GatewaySessionCheckProperties("http://auth:8081", 1000L, 5000L);

        assertThat(props.getAuthBaseUrl()).isEqualTo("http://auth:8081");
        assertThat(props.getConnectTimeoutMs()).isEqualTo(1000L);
        assertThat(props.getReadTimeoutMs()).isEqualTo(5000L);
    }
}
