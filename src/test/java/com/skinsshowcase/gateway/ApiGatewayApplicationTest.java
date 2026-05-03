package com.skinsshowcase.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class ApiGatewayApplicationTest {

    @Test
    void main_startsSpringApplicationWithArgs() {
        try (var mocked = mockStatic(SpringApplication.class)) {
            ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
            mocked.when(() -> SpringApplication.run(eq(ApiGatewayApplication.class), any(String[].class)))
                    .thenReturn(ctx);

            ApiGatewayApplication.main(new String[] { "--spring.main.web-application-type=reactive" });

            mocked.verify(() -> SpringApplication.run(eq(ApiGatewayApplication.class), any(String[].class)), times(1));
        }
    }
}
