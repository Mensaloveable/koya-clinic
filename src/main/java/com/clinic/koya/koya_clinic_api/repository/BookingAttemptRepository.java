package com.clinic.koya.koya_clinic_api.repository;

import com.clinic.koya.koya_clinic_api.model.BookingAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BookingAttemptRepository extends JpaRepository<BookingAttempt, Long> {

    @Query("""
            select a.attemptedAt from BookingAttempt a
            where a.email = :email
            order by a.attemptedAt
            """)
    List<Instant> findRecent(@Param("email") String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from BookingAttempt a
            where a.email = :email and a.attemptedAt <= :cutoff
            """)
    int deleteExpired(@Param("email") String email, @Param("cutoff") Instant cutoff);
}
