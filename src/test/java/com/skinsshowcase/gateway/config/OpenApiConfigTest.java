package com.skinsshowcase.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(OpenApiConfig.class)
@TestPropertySource(properties = {
        "gateway.openapi.public-host=gateway.example",
        "server.port=9099"
})
class OpenApiConfigTest {

    @Autowired
    private OpenAPI openAPI;

    @Test
    void openApiHasGatewayServerAndSecurity() {
        assertThat(openAPI.getServers()).hasSize(1);
        assertThat(openAPI.getServers().get(0).getUrl()).isEqualTo("http://gateway.example:9099");
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        assertThat(openAPI.getPaths()).containsKeys("/auth/session", "/api/v1/items");
    }
}
