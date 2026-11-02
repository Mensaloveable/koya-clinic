package com.clinic.koya.koya_clinic_api.repository;

import com.clinic.koya.koya_clinic_api.model.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") String id);

    boolean existsBySlotIdAndStatus(String slotId, String status);

    @Query("select distinct b.slotId from Booking b where b.status = :status")
    List<String> findSlotIdsByStatus(@Param("status") String status);

    @Query("""
            select b from Booking b
            where b.email = :email
            order by b.createdAt, b.id
            """)
    List<Booking> findHistoryFirst(@Param("email") String email, Pageable pageable);

    @Query("""
            select b from Booking b
            where b.email = :email
              and (b.createdAt > :afterCreatedAt
                   or (b.createdAt = :afterCreatedAt and b.id > :afterId))
            order by b.createdAt, b.id
            """)
    List<Booking> findHistoryAfter(
            @Param("email") String email,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") String afterId,
            Pageable pageable);

    long countByStatus(String status);
}

