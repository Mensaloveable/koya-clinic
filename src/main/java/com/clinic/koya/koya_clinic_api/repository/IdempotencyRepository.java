package com.clinic.koya.koya_clinic_api.repository;

import com.clinic.koya.koya_clinic_api.model.Idempotency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRepository extends JpaRepository<Idempotency, String> {
}
