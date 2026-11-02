package com.clinic.koya.koya_clinic_api.controller.api;

import com.clinic.koya.koya_clinic_api.dto.BookingRequest;
import com.clinic.koya.koya_clinic_api.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<String> create(
            @Valid @RequestBody BookingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return bookingService.create(request, key).response();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return bookingService.get(id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> cancel(@PathVariable String id) {
        return bookingService.cancel(id);
    }

    @GetMapping
    public Map<String, Object> history(
            @RequestParam String email,
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String cursor) {
        return bookingService.history(email, limit, cursor);
    }
}