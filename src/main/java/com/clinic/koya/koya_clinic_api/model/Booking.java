package com.clinic.koya.koya_clinic_api.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    public static final String CONFIRMED = "confirmed";
    public static final String CANCELLED = "cancelled";

    @Id
    @Column(columnDefinition = "text")
    private String id;

    @Column(name = "slot_id", nullable = false, columnDefinition = "text")
    private String slotId;

    @Column(nullable = false, columnDefinition = "text")
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String email;

    @Column(nullable = false, columnDefinition = "text")
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}


