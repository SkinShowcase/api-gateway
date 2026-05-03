package com.skinsshowcase.gateway.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayTcpPropertiesTest {

    @Test
    void holdsConfiguredValues() {
        var props = new GatewayTcpProperties(9092, "messaging.internal", 9090);

        assertThat(props.getPort()).isEqualTo(9092);
        assertThat(props.getMessagingHost()).isEqualTo("messaging.internal");
        assertThat(props.getMessagingPort()).isEqualTo(9090);
    }
}
