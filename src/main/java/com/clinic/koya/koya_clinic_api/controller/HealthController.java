package com.clinic.koya.koya_clinic_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RequestMapping("/health")
@RestControllerAdvice
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> app() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Koya Clinic App",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
