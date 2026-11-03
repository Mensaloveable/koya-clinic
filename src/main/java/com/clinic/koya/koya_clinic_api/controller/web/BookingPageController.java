package com.clinic.koya.koya_clinic_api.controller.web;

import com.clinic.koya.koya_clinic_api.config.security.RequestSecurity;
import com.clinic.koya.koya_clinic_api.dto.BookingRequest;
import com.clinic.koya.koya_clinic_api.dto.Outcome;
import com.clinic.koya.koya_clinic_api.exception.ApiFailure;
import com.clinic.koya.koya_clinic_api.service.BookingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class BookingPageController {

    private static final Logger log = LoggerFactory.getLogger(BookingPageController.class);
    private static final String PROVIDER_ATTRIBUTE = "provider";
    private static final String MESSAGE_ATTRIBUTE = "message";
    private static final String INTENT_ATTRIBUTE = "intent";

    private final BookingService bookingService;
    private final ObjectMapper objectMapper;


    @GetMapping({"/", "/web", "/web/"})
    public String home(
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String booking,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request,
            Model model
    ) {
        model.addAttribute(PROVIDER_ATTRIBUTE, provider);

        try {
            if (booking != null) {
                model.addAttribute("booking", bookingService.get(booking));
            }

            if (email != null) {
                model.addAttribute("history", bookingService.history(email, "5", cursor));
                model.addAttribute("historyEmail", email);
            }

        } catch (ApiFailure ex) {
            log.debug("Returning booking page with error: status={} message={}", ex.getStatus(), ex.getMessage());
            model.addAttribute(MESSAGE_ATTRIBUTE, ex.getMessage());
        }

        return render(model, request);
    }

    @PostMapping("/web/book")
    public String book(
            @RequestParam("slot_id") String slotId,
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String intent,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) {

        model.addAttribute(INTENT_ATTRIBUTE, intent);
        model.addAttribute("name", name);
        model.addAttribute("formEmail", email);
        model.addAttribute("selectedSlot", slotId);

        BookingRequest requestDto = new BookingRequest(slotId, name, email);
        Outcome result = bookingService.create(requestDto, intent);

        if (result.statusCode() == HttpStatus.CREATED.value()) {
            String id = objectMapper.readTree(result.body()).get("id").asString();
            log.info("Web booking confirmed: id={} slot={}", id, slotId);
            return "redirect:/web/?booking=" + id + "#confirmation";
        }

        response.setStatus(result.statusCode());
        if (result.retryAfter() != null) {
            response.setHeader("Retry-After", result.retryAfter().toString());
            model.addAttribute(MESSAGE_ATTRIBUTE,
                    "Too many attempts. Try again in " + result.retryAfter() + " seconds.");
        } else {
            model.addAttribute(MESSAGE_ATTRIBUTE, objectMapper.readTree(result.body()).get("error").asString());
        }

        log.debug("Web booking failed: status={} retryAfter={}", result.statusCode(), result.retryAfter());
        return render(model, request);
    }

    @PostMapping("/web/cancel")
    public String cancel(@RequestParam String id) {

        bookingService.cancel(id);
        log.info("Web booking cancelled: id={}", id);
        return "redirect:/web/?booking=" + URLEncoder.encode(id, StandardCharsets.UTF_8) + "#confirmation";
    }

    @ExceptionHandler(ApiFailure.class)
    public String failure(
            ApiFailure ex,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response) {
        log.warn("Web request failed: status={} message={}", ex.getStatus(), ex.getMessage());
        response.setStatus(ex.getStatus());
        model.addAttribute(MESSAGE_ATTRIBUTE, ex.getMessage());
        return render(model, request);
    }

    private String render(Model model, HttpServletRequest request) {
        List<Map<String, Object>> all = allSlots();
        model.addAttribute("providers", all.stream()
                .map(slot -> slot.get(PROVIDER_ATTRIBUTE).toString())
                .distinct()
                .sorted()
                .toList());
        model.addAttribute("allSlots", all);
        model.addAttribute("now", bookingService.now());

        String provider = (String) model.getAttribute(PROVIDER_ATTRIBUTE);
        model.addAttribute("slots", provider == null || provider.isEmpty()
                ? all
                : all.stream().filter(slot -> provider.equals(slot.get(PROVIDER_ATTRIBUTE))).toList());
        model.addAttribute("csrf", RequestSecurity.csrf(request));
        if (!model.containsAttribute(INTENT_ATTRIBUTE)) {
            model.addAttribute(INTENT_ATTRIBUTE, UUID.randomUUID().toString());
        }
        return "index";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> allSlots() {
        return (List<Map<String, Object>>) bookingService.slots(null).get("slots");
    }
}
