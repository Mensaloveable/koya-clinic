package com.clinic.koya.koya_clinic_api.service;

import com.clinic.koya.koya_clinic_api.model.BookingAttempt;
import com.clinic.koya.koya_clinic_api.repository.AdvisoryLockRepository;
import com.clinic.koya.koya_clinic_api.repository.BookingAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 60;

    private final BookingAttemptRepository attempts;
    private final AdvisoryLockRepository locks;
    private final Clock clock;

    // Separate commit: a rejected booking or crash must not refund an attempt.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Integer attempt(String email) {

        locks.lockClinicDataShared();
        locks.lockEmail(email);

        Instant now = clock.instant();
        attempts.deleteExpired(email, now.minusSeconds(WINDOW_SECONDS));

        List<Instant> times = attempts.findRecent(email);
        if (times.size() >= MAX_ATTEMPTS) {
            int retryAfter = Math.max(1, (int) Math.ceil(
                    Duration.between(now, times.getFirst().plusSeconds(WINDOW_SECONDS)).toMillis() / 1000.0));

            log.warn("Rate limit reached: retryAfterSeconds={}", retryAfter);

            return retryAfter;
        }

        attempts.save(new BookingAttempt(email, now));
        log.debug("Recorded booking attempt: attemptsInWindow={}", times.size() + 1);
        return null;
    }
}
