package com.clinic.koya.koya_clinic_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clinic.pagination")
public record PaginationProperties(int minLimit, int maxLimit, int defaultLimit) {
}
