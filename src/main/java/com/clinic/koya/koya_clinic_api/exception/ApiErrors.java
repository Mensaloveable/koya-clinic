package com.clinic.koya.koya_clinic_api.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;


@RestControllerAdvice(basePackages = "com.clinic.koya.koya_clinic_api")
public class ApiErrors {

    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        int status = HttpStatus.INTERNAL_SERVER_ERROR.value();
        String message = "An error occurred, Please contact support";

        if (ex instanceof ApiFailure apiFailure) {
            status = apiFailure.getStatus();
            message = Objects.requireNonNullElse(apiFailure.getMessage().trim(), "Request failed");

        } else if (ex instanceof HttpMessageNotReadableException) {
            status = HttpStatus.BAD_REQUEST.value();
            message = "Invalid request payload";

        } else if (ex instanceof ErrorResponse errorResponse) {
            status = errorResponse.getStatusCode().value();
            message = status == HttpStatus.BAD_REQUEST.value()
                    ? "invalid request"
                    : HttpStatus.valueOf(status).getReasonPhrase().toLowerCase(Locale.ROOT);
        }

        if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.error("Unhandled API failure", ex);
        } else {
            log.debug("API request failed: status={} message={}", status, message);
        }

        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
