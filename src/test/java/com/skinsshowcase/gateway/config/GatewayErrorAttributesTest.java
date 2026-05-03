package com.skinsshowcase.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayErrorAttributesTest {

    private static final String ERROR_ATTRIBUTE =
            "org.springframework.boot.web.reactive.error.DefaultErrorAttributes.ERROR";

    private GatewayErrorAttributes attributes;

    @BeforeEach
    void setUp() {
        attributes = new GatewayErrorAttributes();
    }

    @Test
    void getError_returnsNull_whenAttributeMissing() {
        ServerRequest request = mock(ServerRequest.class);
        when(request.attribute(ERROR_ATTRIBUTE)).thenReturn(Optional.empty());

        assertThat(attributes.getError(request)).isNull();
    }

    @Test
    void getError_returnsThrowable_whenAttributeIsThrowable() {
        var ex = new IllegalStateException("boom");
        ServerRequest request = mock(ServerRequest.class);
        when(request.attribute(ERROR_ATTRIBUTE)).thenReturn(Optional.of(ex));

        assertThat(attributes.getError(request)).isSameAs(ex);
    }

    @Test
    void getError_returnsNull_whenAttributeIsNotThrowable() {
        ServerRequest request = mock(ServerRequest.class);
        when(request.attribute(ERROR_ATTRIBUTE)).thenReturn(Optional.of("not-a-throwable"));

        assertThat(attributes.getError(request)).isNull();
    }

    @Test
    void getErrorAttributes_usesResponseStatusExceptionStatusAndReason() {
        ServerRequest request = mock(ServerRequest.class);
        when(request.attribute(ERROR_ATTRIBUTE))
                .thenReturn(Optional.of(new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid")));
        when(request.path()).thenReturn("/api/x");

        var map = attributes.getErrorAttributes(request, ErrorAttributeOptions.defaults());

        assertThat(map)
                .containsEntry("status", 400)
                .containsEntry("error", "Bad Request")
                .containsEntry("message", "invalid")
                .containsEntry("path", "/api/x");
    }

    @Test
    void getErrorAttributes_usesCause_whenWrapped() {
        ServerRequest request = mock(ServerRequest.class);
        var cause = new ResponseStatusException(HttpStatus.NOT_FOUND, "gone");
        when(request.attribute(ERROR_ATTRIBUTE)).thenReturn(Optional.of(new RuntimeException(cause)));
        when(request.path()).thenReturn("/p");

        var map = attributes.getErrorAttributes(request, ErrorAttributeOptions.defaults());

        assertThat(map)
                .containsEntry("status", 404)
                .containsEntry("message", "gone");
    }

    @Test
    void getErrorAttributes_defaultMessageFor404WithoutReason() {
        ServerRequest request = mock(ServerRequest.class);
        when(request.attribute(ERROR_ATTRIBUTE))
                .thenReturn(Optional.of(new ResponseStatusException(HttpStatus.NOT_FOUND)));
        when(request.path()).thenReturn("/missing");

        var map = attributes.getErrorAttributes(request, ErrorAttributeOptions.defaults());

        assertThat(map.get("message")).isEqualTo("No route found");
    }
}
