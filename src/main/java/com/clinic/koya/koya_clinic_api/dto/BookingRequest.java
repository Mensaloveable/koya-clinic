package com.clinic.koya.koya_clinic_api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record BookingRequest(
        @NotBlank(message = "slot_id is required")
        @JsonProperty("slot_id")
        String slotId,

        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "email is required")
        String email
) {
}
