package com.clinic.koya.koya_clinic_api.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class AdvisoryLockRepository {

    private static final long CLINIC_DATA_LOCK = 4242L;
    private static final long EMAIL_LOCK_NAMESPACE = 1L;
    private static final long IDEMPOTENCY_LOCK_NAMESPACE = 2L;

    @PersistenceContext
    private EntityManager em;

    public void lockClinicDataShared() {
        em.createNativeQuery("SELECT pg_advisory_xact_lock_shared(:key)")
                .setParameter("key", CLINIC_DATA_LOCK)
                .getResultList();
    }

    public void lockClinicDataExclusive() {
        em.createNativeQuery("SELECT pg_advisory_xact_lock(:key)")
                .setParameter("key", CLINIC_DATA_LOCK)
                .getResultList();
    }

    public void lockEmail(String email) {
        hashLock(email, EMAIL_LOCK_NAMESPACE);
    }

    public void lockIdempotencyKey(String key) {
        hashLock(key, IDEMPOTENCY_LOCK_NAMESPACE);
    }

    private void hashLock(String value, long namespace) {
        em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:value, :namespace))")
                .setParameter("value", value)
                .setParameter("namespace", namespace)
                .getResultList();
    }
}
