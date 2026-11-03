package com.clinic.koya.koya_clinic_api.controller.web;

import com.clinic.koya.koya_clinic_api.config.security.ApiMappingSecurity;
import com.clinic.koya.koya_clinic_api.config.security.RequestSecurity;
import com.clinic.koya.koya_clinic_api.controller.api.SlotController;
import com.clinic.koya.koya_clinic_api.dto.BookingRequest;
import com.clinic.koya.koya_clinic_api.dto.Outcome;
import com.clinic.koya.koya_clinic_api.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {BookingPageController.class, SlotController.class},
        properties = "clinic.api-key=routing-test-key")
@Import({RequestSecurity.class, ApiMappingSecurity.class})
class BookingWebRoutingTest {
    @Autowired MockMvc mvc;
    @MockitoBean BookingService service;

    @BeforeEach void slots() {
        when(service.now()).thenReturn(Instant.parse("2030-01-01T00:00:00Z"));
        when(service.slots(null)).thenReturn(Map.of("slots", List.of(Map.of(
                "id", "s1", "provider", "Dr Ade", "start", "2030-01-02T09:00:00Z",
                "end", "2030-01-02T09:30:00Z", "available", true))));
    }

    @Test void homeRoutesThroughControllerAndRendersClinician() throws Exception {
        mvc.perform(get("/web/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("providers", List.of("Dr Ade")))
                .andExpect(content().string(containsString("Dr Ade")))
                .andExpect(content().string(containsString("name=\"csrf\"")));
    }

    @Test void rootHomeLoadsCliniciansInsteadOfEmptyWelcomeTemplate() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("providers", List.of("Dr Ade")))
                .andExpect(content().string(containsString("Dr Ade")));
        mvc.perform(get("/web"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("providers", List.of("Dr Ade")));
    }

    @Test void rootProviderFilterUsesControllerModel() throws Exception {
        mvc.perform(get("/").param("provider", "Dr Ade"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("provider", "Dr Ade"))
                .andExpect(content().string(containsString("Dr Ade")));
        mvc.perform(get("/").param("provider", "No such clinician"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No appointments to show")));
    }

    @Test void pastSlotsStillPopulateCliniciansOnRootAndWebHome() throws Exception {
        when(service.now()).thenReturn(Instant.parse("2026-11-04T00:00:00Z"));
        when(service.slots(null)).thenReturn(Map.of("slots", List.of(
                Map.of("id", "slot-001", "provider", "DrSmith", "start", "2026-11-03T09:00:00Z",
                        "end", "2026-11-03T10:00:00Z", "available", true),
                Map.of("id", "slot-002", "provider", "DrJones", "start", "2026-11-03T10:00:00Z",
                        "end", "2026-11-03T11:00:00Z", "available", true),
                Map.of("id", "slot-003", "provider", "DrKoya", "start", "2026-11-03T11:00:00Z",
                        "end", "2026-11-03T15:00:00Z", "available", true))));
        for (String path : List.of("/", "/web/")) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("providers", List.of("DrJones", "DrKoya", "DrSmith")))
                    .andExpect(content().string(containsString("DrSmith")))
                    .andExpect(content().string(containsString("DrJones")))
                    .andExpect(content().string(containsString("DrKoya")))
                    .andExpect(content().string(containsString("Past")));
        }
    }

    @Test void apiRequiresKeyAndAcceptsCorrectKey() throws Exception {
        mvc.perform(get("/api/slots").servletPath("/api/slots"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"unauthorized\"}"));
        mvc.perform(get("/api/slots").servletPath("/api/slots").header("X-API-Key", "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/slots").servletPath("/api/slots").header("X-API-Key", "routing-test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].provider").value("Dr Ade"));
    }

    @Test void bookingRejectsMissingCsrfAndAcceptsSessionToken() throws Exception {
        mvc.perform(post("/web/book").servletPath("/web/book").param("slot_id", "s1").param("name", "Ada")
                .param("email", "ada@example.com").param("intent", "intent-1"))
                .andExpect(status().isForbidden());
        var home = mvc.perform(get("/web/")).andReturn();
        var session = (MockHttpSession) home.getRequest().getSession(false);
        when(service.create(any(BookingRequest.class), eq("intent-1")))
                .thenReturn(new Outcome(201, "{\"id\":\"b1\"}", null));
        mvc.perform(post("/web/book").servletPath("/web/book").session(session)
                .param("csrf", (String) session.getAttribute("csrf"))
                .param("slot_id", "s1").param("name", "Ada")
                .param("email", "ada@example.com").param("intent", "intent-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/web/?booking=b1#confirmation"));
    }
}
