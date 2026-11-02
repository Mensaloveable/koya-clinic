package com.clinic.koya.koya_clinic_api.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "booking_attempts")
@Getter
@Setter
@NoArgsConstructor
public class BookingAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "text")
    private String email;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    public BookingAttempt(String email, Instant attemptedAt) {
        this.email = email;
        this.attemptedAt = attemptedAt;
    }
}
