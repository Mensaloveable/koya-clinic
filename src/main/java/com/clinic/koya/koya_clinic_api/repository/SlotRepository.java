package com.clinic.koya.koya_clinic_api.repository;

import com.clinic.koya.koya_clinic_api.model.Slot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SlotRepository extends JpaRepository<Slot, String> {

    List<Slot> findAllByOrderByStartAtAscIdAsc();

    List<Slot> findByProviderOrderByStartAtAscIdAsc(String provider);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Slot s where s.id = :id")
    Optional<Slot> findByIdForUpdate(@Param("id") String id);
}
