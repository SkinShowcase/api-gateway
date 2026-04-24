package com.skinsshowcase.gateway.config;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Формирует тело ошибки для ответа gateway в виде JSON-полей.
 * Не пробрасывает stack trace и чувствительные данные на фронт.
 * Не бросает, если в exchange нет атрибута исключения (например GET "/" без маршрута).
 */
@Component
public class GatewayErrorAttributes extends DefaultErrorAttributes {

    private static final String ERROR_ATTRIBUTE =
            "org.springframework.boot.web.reactive.error.DefaultErrorAttributes.ERROR";

    @Override
    public Throwable getError(ServerRequest request) {
        var value = request.attribute(ERROR_ATTRIBUTE).orElse(null);
        return value instanceof Throwable t ? t : null;
    }

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        var error = getError(request);
        var status = getHttpStatus(error);
        var map = new LinkedHashMap<String, Object>();
        map.put("status", status);
        map.put("error", getErrorReason(status));
        map.put("message", getErrorMessage(error, status));
        map.put("path", request.path());
        return map;
    }

    private static int getHttpStatus(Throwable error) {
        var statusValue = org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR.value();
        if (error instanceof org.springframework.web.server.ResponseStatusException rse) {
            var code = rse.getStatusCode();
            if (code.isError()) {
                statusValue = code.value();
            }
        }
        if (error != null && error.getCause() instanceof org.springframework.web.server.ResponseStatusException rse) {
            var code = rse.getStatusCode();
            if (code.isError()) {
                statusValue = code.value();
            }
        }
        return statusValue;
    }

    private static String getErrorReason(int status) {
        return org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase();
    }

    private static String getErrorMessage(Throwable error, int status) {
        if (error instanceof org.springframework.web.server.ResponseStatusException rse
                && rse.getReason() != null && !rse.getReason().isBlank()) {
            return rse.getReason();
        }
        if (error != null && error.getCause() instanceof org.springframework.web.server.ResponseStatusException rse
                && rse.getReason() != null && !rse.getReason().isBlank()) {
            return rse.getReason();
        }
        return switch (status) {
            case 404 -> "No route found";
            case 502, 503 -> "Service temporarily unavailable";
            default -> error != null && error.getMessage() != null ? error.getMessage() : "Internal server error";
        };
    }
}
