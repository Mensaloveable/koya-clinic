package com.clinic.koya.koya_clinic_api.dto;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public record Outcome(
        int statusCode,
        String body,
        Integer retryAfter
) {
    public ResponseEntity<String> response() {
        ResponseEntity.BodyBuilder bodyBuilder = ResponseEntity.status(statusCode)
                .contentType(MediaType.APPLICATION_JSON);

        if (retryAfter != null) {
            bodyBuilder.header("Retry-After", retryAfter.toString());
        }

        return bodyBuilder.body(body);
    }
}
