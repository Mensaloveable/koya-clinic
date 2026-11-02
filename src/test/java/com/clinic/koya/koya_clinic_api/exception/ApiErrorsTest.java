package com.clinic.koya.koya_clinic_api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ApiErrorsTest {

    private final ApiErrors apiErrors = new ApiErrors();

    @Test
    void handlesApiFailureWithItsStatusAndMessage() {
        ResponseEntity<Map<String, String>> response =
                apiErrors.handleException(new ApiFailure(409, "slot already booked"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).containsEntry("error", "slot already booked");
    }

    @Test
    void handlesUnreadablePayloadAsBadRequest() {
        ResponseEntity<Map<String, String>> response =
                apiErrors.handleException(mock(HttpMessageNotReadableException.class));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("error", "Invalid request payload");
    }

    @Test
    void handlesBadRequestErrorResponse() {
        ResponseEntity<Map<String, String>> response =
                apiErrors.handleException(new ResponseStatusException(HttpStatus.BAD_REQUEST));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("error", "invalid request");
    }

    @Test
    void handlesOtherErrorResponseUsingReasonPhrase() {
        ResponseEntity<Map<String, String>> response =
                apiErrors.handleException(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).containsEntry("error", "not found");
    }

    @Test
    void hidesUnexpectedExceptionDetails() {
        ResponseEntity<Map<String, String>> response =
                apiErrors.handleException(new IllegalStateException("database password is secret"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).containsEntry("error", "An error occurred, Please contact support");
        assertThat(response.getBody().get("error")).doesNotContain("database");
    }
}
