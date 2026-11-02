package com.clinic.koya.koya_clinic_api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "slots")
@Getter
@Setter
@NoArgsConstructor
public class Slot {

    @Id
    @Column(columnDefinition = "text")
    private String id;

    @Column(nullable = false, columnDefinition = "text")
    private String provider;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Transient
    private boolean available;

    public Slot(String id, String provider, Instant startAt, Instant endAt) {
        this.id = id;
        this.provider = provider;
        this.startAt = startAt;
        this.endAt = endAt;
    }
}
