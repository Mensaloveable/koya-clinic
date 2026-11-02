package com.clinic.koya.koya_clinic_api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "idempotency")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Idempotency {

    @Id
    @Column(name = "key", columnDefinition = "text")
    private String key;

    @Column(name = "request_body", nullable = false, columnDefinition = "text")
    private String requestBody;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "response_body", nullable = false, columnDefinition = "text")
    private String responseBody;
}
