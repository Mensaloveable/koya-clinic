package com.clinic.koya.koya_clinic_api.controller.api;

import com.clinic.koya.koya_clinic_api.dto.ResetRequest;
import com.clinic.koya.koya_clinic_api.model.SlotDefinition;
import com.clinic.koya.koya_clinic_api.service.BookingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResetControllerTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private ResetController controller;

    @Test
    void resetDelegatesToService() {
        ResetRequest request = new ResetRequest(List.of(
                new SlotDefinition("s1", "Dr Ada", Instant.parse("2026-01-01T10:00:00Z"),
                        Instant.parse("2026-01-01T10:30:00Z"))));

        ResponseEntity<Void> response = controller.reset(request);

        verify(bookingService).reset(request);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNull();
    }
}
