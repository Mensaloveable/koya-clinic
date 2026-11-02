package com.clinic.koya.koya_clinic_api.dto;

import com.clinic.koya.koya_clinic_api.model.SlotDefinition;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ResetRequest(
        @NotNull(message = "slots is required")
        @Valid
        @JsonProperty("slots")
        List<SlotDefinition> slots
) {
}

