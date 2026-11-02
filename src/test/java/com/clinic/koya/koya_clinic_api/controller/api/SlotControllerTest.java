package com.clinic.koya.koya_clinic_api.controller.api;

import com.clinic.koya.koya_clinic_api.service.BookingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotControllerTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private SlotController controller;

    @Test
    void listDelegatesToService() {
        Map<String, Object> slots = Map.of("slots", List.of());
        when(bookingService.slots("Dr Ada")).thenReturn(slots);

        assertThat(controller.list("Dr Ada")).isEqualTo(slots);
    }

    @Test
    void listAllowsMissingProvider() {
        Map<String, Object> slots = Map.of("slots", List.of());
        when(bookingService.slots(null)).thenReturn(slots);

        assertThat(controller.list(null)).isEqualTo(slots);
    }
}
