package com.clinic.koya.koya_clinic_api.controller.api;

import com.clinic.koya.koya_clinic_api.service.BookingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/slots")
public class SlotController {

    private final BookingService bookingService;

    public SlotController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String provider) {
        return bookingService.slots(provider);
    }
}
