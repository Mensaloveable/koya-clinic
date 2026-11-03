package com.clinic.koya.koya_clinic_api.controller.api;

import com.clinic.koya.koya_clinic_api.dto.ResetRequest;
import com.clinic.koya.koya_clinic_api.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reset")
public class ResetController {

    private final BookingService bookingService;

    public ResetController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetRequest request) {
        bookingService.reset(request);
        return ResponseEntity.ok().build();
    }
}
