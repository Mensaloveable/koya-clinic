package com.clinic.koya.koya_clinic_api.service;

import com.clinic.koya.koya_clinic_api.model.BookingAttempt;
import com.clinic.koya.koya_clinic_api.repository.AdvisoryLockRepository;
import com.clinic.koya.koya_clinic_api.repository.BookingAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final String EMAIL = "ada@example.com";

    @Mock
    private BookingAttemptRepository attempts;

    @Mock
    private AdvisoryLockRepository locks;

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(attempts, locks, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void attemptRecordsAndAllowsBookingUnderLimit() {
        when(attempts.findRecent(EMAIL)).thenReturn(List.of());

        Integer retryAfter = rateLimiter.attempt(EMAIL);

        assertThat(retryAfter).isNull();
        verify(locks).lockClinicDataShared();
        verify(locks).lockEmail(EMAIL);
        verify(attempts).deleteExpired(EMAIL, NOW.minusSeconds(60));

        ArgumentCaptor<BookingAttempt> captor = ArgumentCaptor.forClass(BookingAttempt.class);
        verify(attempts).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(captor.getValue().getAttemptedAt()).isEqualTo(NOW);
    }

    @Test
    void attemptAllowsTheFifthAttemptInWindow() {
        Instant first = NOW.minusSeconds(50);
        when(attempts.findRecent(EMAIL)).thenReturn(List.of(first, NOW.minusSeconds(40), NOW.minusSeconds(30),
                NOW.minusSeconds(10)));

        assertThat(rateLimiter.attempt(EMAIL)).isNull();
        verify(attempts).save(any(BookingAttempt.class));
    }

    @Test
    void attemptReturnsRetryAfterWhenLimitReached() {
        Instant first = NOW.minusSeconds(50);
        when(attempts.findRecent(EMAIL)).thenReturn(List.of(first, NOW.minusSeconds(40), NOW.minusSeconds(30),
                NOW.minusSeconds(20), NOW.minusSeconds(10)));

        Integer retryAfter = rateLimiter.attempt(EMAIL);

        assertThat(retryAfter).isEqualTo(10);
        verify(attempts, never()).save(any(BookingAttempt.class));
    }

    @Test
    void retryAfterIsAlwaysAtLeastOneSecond() {
        Instant oldest = NOW.plusSeconds(1);
        when(attempts.findRecent(EMAIL)).thenReturn(List.of(oldest, oldest, oldest, oldest, oldest));

        Integer retryAfter = rateLimiter.attempt(EMAIL);

        assertThat(retryAfter).isGreaterThanOrEqualTo(1);
    }

    @Test
    void attemptUsesNewestAttemptAtWindowBoundary() {
        when(attempts.findRecent(EMAIL)).thenReturn(List.of(
                NOW.minusSeconds(59), NOW.minusSeconds(50), NOW.minusSeconds(40), NOW.minusSeconds(30),
                NOW.minusSeconds(20)));

        assertThat(rateLimiter.attempt(EMAIL)).isEqualTo(1);
    }
}
