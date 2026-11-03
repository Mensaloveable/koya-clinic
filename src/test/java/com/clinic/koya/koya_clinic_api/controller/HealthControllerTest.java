package com.clinic.koya.koya_clinic_api.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @Test
    void appReportsUp() {
        var response = controller.app();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("status", "UP");
        assertThat(response.getBody()).containsEntry("service", "Koya Clinic App");
        assertThat((Long) response.getBody().get("timestamp")).isPositive();
    }
}
