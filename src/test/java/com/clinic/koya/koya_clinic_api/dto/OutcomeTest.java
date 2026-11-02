package com.clinic.koya.koya_clinic_api.dto;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class OutcomeTest {

    @Test
    void responseCarriesStatusBodyAndJsonContentType() {
        ResponseEntity<String> response = new Outcome(201, "{\"id\":\"b1\"}", null).response();

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo("{\"id\":\"b1\"}");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getHeaders().getFirst("Retry-After")).isNull();
    }

    @Test
    void responseAddsRetryAfterHeaderWhenPresent() {
        ResponseEntity<String> response = new Outcome(429, "{\"error\":\"slow down\"}", 30).response();

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("30");
    }
}
