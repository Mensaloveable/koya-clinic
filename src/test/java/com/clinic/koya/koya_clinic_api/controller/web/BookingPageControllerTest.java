package com.clinic.koya.koya_clinic_api.controller.web;

import com.clinic.koya.koya_clinic_api.dto.BookingRequest;
import com.clinic.koya.koya_clinic_api.dto.Outcome;
import com.clinic.koya.koya_clinic_api.exception.ApiFailure;
import com.clinic.koya.koya_clinic_api.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingPageControllerTest {

    @Mock
    private BookingService bookingService;

    private BookingPageController controller;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private Model model;

    @BeforeEach
    void setUp() {
        controller = new BookingPageController(bookingService, new ObjectMapper());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        model = new ExtendedModelMap();

        when(bookingService.slots(null)).thenReturn(Map.of("slots", List.of(
                Map.of("id", "s1", "provider", "Dr Ada"),
                Map.of("id", "s2", "provider", "Dr Bola")
        )));
        when(bookingService.now()).thenReturn(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void homeRendersIndexWithAllSlots() {
        String view = controller.home(null, null, null, null, request, model);

        assertThat(view).isEqualTo("index");
        assertThat(model.getAttribute("providers")).isEqualTo(List.of("Dr Ada", "Dr Bola"));
        assertThat((List<?>) model.getAttribute("allSlots")).hasSize(2);
        assertThat((List<?>) model.getAttribute("slots")).hasSize(2);
        assertThat(model.getAttribute("now")).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(model.getAttribute("csrf")).isNotNull();
        assertThat(model.getAttribute("intent")).isNotNull();
    }

    @Test
    void homeFiltersSlotsByProvider() {
        controller.home("Dr Bola", null, null, null, request, model);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots = (List<Map<String, Object>>) model.getAttribute("slots");
        assertThat(slots).extracting(slot -> slot.get("id")).containsExactly("s2");
    }

    @Test
    void homeLoadsSingleBooking() {
        when(bookingService.get("b1")).thenReturn(Map.of("id", "b1", "status", "confirmed"));

        controller.home(null, "b1", null, null, request, model);

        assertThat(model.getAttribute("booking")).isEqualTo(Map.of("id", "b1", "status", "confirmed"));
    }

    @Test
    void homeLoadsBookingHistory() {
        Map<String, Object> history = Map.of("bookings", List.of(), "next_cursor", "abc");
        when(bookingService.history("ada@example.com", "5", "cursor-1")).thenReturn(history);

        controller.home(null, null, "ada@example.com", "cursor-1", request, model);

        assertThat(model.getAttribute("history")).isEqualTo(history);
        assertThat(model.getAttribute("historyEmail")).isEqualTo("ada@example.com");
    }

    @Test
    void homeRendersErrorWhenLookupFails() {
        when(bookingService.get("missing")).thenThrow(new ApiFailure(404, "booking not found"));

        String view = controller.home(null, "missing", null, null, request, model);

        assertThat(view).isEqualTo("index");
        assertThat(model.getAttribute("message")).isEqualTo("booking not found");
        assertThat(model.getAttribute("booking")).isNull();
    }

    @Test
    void bookRedirectsToConfirmationOnSuccess() throws Exception {
        when(bookingService.create(any(BookingRequest.class), eq("intent-1")))
                .thenReturn(new Outcome(201, "{\"id\":\"b1\"}", null));

        String view = controller.book("s1", "Ada", "ada@example.com", "intent-1", request, response, model);

        assertThat(view).isEqualTo("redirect:/web/?booking=b1#confirmation");
        assertThat(model.getAttribute("intent")).isEqualTo("intent-1");
        assertThat(model.getAttribute("name")).isEqualTo("Ada");
        assertThat(model.getAttribute("formEmail")).isEqualTo("ada@example.com");
        assertThat(model.getAttribute("selectedSlot")).isEqualTo("s1");
    }

    @Test
    void bookRendersRateLimitMessageWithRetryAfter() throws Exception {
        when(bookingService.create(any(BookingRequest.class), eq("intent-1")))
                .thenReturn(new Outcome(429, "{\"error\":\"too many booking attempts\"}", 15));

        String view = controller.book("s1", "Ada", "ada@example.com", "intent-1", request, response, model);

        assertThat(view).isEqualTo("index");
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("15");
        assertThat(model.getAttribute("message")).isEqualTo("Too many attempts. Try again in 15 seconds.");
    }

    @Test
    void bookRendersApiErrorMessage() throws Exception {
        when(bookingService.create(any(BookingRequest.class), eq("intent-1")))
                .thenReturn(new Outcome(409, "{\"error\":\"slot already booked\"}", null));

        String view = controller.book("s1", "Ada", "ada@example.com", "intent-1", request, response, model);

        assertThat(view).isEqualTo("index");
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(model.getAttribute("message")).isEqualTo("slot already booked");
    }

    @Test
    void cancelRedirectsBackToConfirmation() {
        String view = controller.cancel("b1");

        assertThat(view).isEqualTo("redirect:/web/?booking=b1#confirmation");
        verify(bookingService).cancel("b1");
    }

    @Test
    void failureRendersStatusAndMessage() {
        String view = controller.failure(new ApiFailure(409, "booking already cancelled"),
                model, request, response);

        assertThat(view).isEqualTo("index");
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(model.getAttribute("message")).isEqualTo("booking already cancelled");
    }

    @Test
    void renderKeepsSubmittedIntentWhenAlreadyPresent() {
        model.addAttribute("intent", "existing-intent");

        controller.home(null, null, null, null, request, model);

        assertThat(model.getAttribute("intent")).isEqualTo("existing-intent");
    }
}
