package com.clinic.koya.koya_clinic_api.controller.api;

import com.clinic.koya.koya_clinic_api.dto.BookingRequest;
import com.clinic.koya.koya_clinic_api.dto.Outcome;
import com.clinic.koya.koya_clinic_api.service.BookingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingController controller;

    @Test
    void createReturnsServiceResponse() {
        BookingRequest request = new BookingRequest("s1", "Ada", "ada@example.com");
        when(bookingService.create(request, "key-1"))
                .thenReturn(new Outcome(201, "{\"id\":\"b1\"}", null));

        ResponseEntity<String> response = controller.create(request, "key-1");

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo("{\"id\":\"b1\"}");
    }

    @Test
    void getDelegatesToService() {
        Map<String, Object> booking = Map.of("id", "b1");
        when(bookingService.get("b1")).thenReturn(booking);

        assertThat(controller.get("b1")).isEqualTo(booking);
    }

    @Test
    void cancelDelegatesToService() {
        Map<String, Object> booking = Map.of("id", "b1", "status", "cancelled");
        when(bookingService.cancel("b1")).thenReturn(booking);

        assertThat(controller.cancel("b1")).isEqualTo(booking);
    }

    @Test
    void historyDelegatesToService() {
        Map<String, Object> history = Map.of("bookings", java.util.List.of());
        when(bookingService.history("ada@example.com", "5", "cursor")).thenReturn(history);

        assertThat(controller.history("ada@example.com", "5", "cursor")).isEqualTo(history);
    }
}
