package com.clinic.koya.koya_clinic_api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record SlotDefinition(
        @NotBlank(message = "id is required")
        String id,

        @NotBlank(message = "provider is required")
        String provider,

        @NotNull(message = "start is required")
        @JsonProperty("start")
        Instant start,

        @NotNull(message = "end is required")
        @JsonProperty("end")
        Instant end) {
}
