package com.clinic.koya.koya_clinic_api.service;

import com.clinic.koya.koya_clinic_api.config.PaginationProperties;
import com.clinic.koya.koya_clinic_api.dto.BookingRequest;
import com.clinic.koya.koya_clinic_api.dto.Outcome;
import com.clinic.koya.koya_clinic_api.dto.ResetRequest;
import com.clinic.koya.koya_clinic_api.exception.ApiFailure;
import com.clinic.koya.koya_clinic_api.model.Booking;
import com.clinic.koya.koya_clinic_api.model.Idempotency;
import com.clinic.koya.koya_clinic_api.model.Slot;
import com.clinic.koya.koya_clinic_api.model.SlotDefinition;
import com.clinic.koya.koya_clinic_api.repository.*;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private static final int MAX_TEXT_LENGTH = 1000;
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 200;
    private static final int MAX_CURSOR_LENGTH = 2000;

    private final PaginationProperties pagination;
    private static final String INVALID_LIMIT_ERROR = "limit must be an integer from %d to %d";

    private final SlotRepository slotRepository;
    private final BookingRepository bookingRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final BookingAttemptRepository attemptRepository;
    private final AdvisoryLockRepository locks;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RateLimiter limiter;
    private final Validator validator;
    private final TransactionTemplate transactions;


    record Address(@Email String value) {
    }

    public Instant now() {
        return clock.instant();
    }

    public Map<String, Object> get(String id) {
        locks.lockClinicDataShared();

        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ApiFailure(404, "booking not found"));
        log.debug("Loaded booking: id={}", id);

        return toMap(booking);
    }

    public void reset(ResetRequest request) {
        if (request == null || request.slots() == null) {
            throw new ApiFailure(400, "slots must be an array");
        }

        ArrayList<SlotDefinition> seeds = new ArrayList<>();
        HashSet<String> ids = new HashSet<>();

        for (SlotDefinition slot : request.slots()) {
            if (!slot.end().isAfter(slot.start()) || !ids.add(slot.id())) {
                throw new ApiFailure(400, "duplicate slot or invalid time range");
            }
            seeds.add(slot);
        }

        locks.lockClinicDataExclusive();
        idempotencyRepository.deleteAllInBatch();
        attemptRepository.deleteAllInBatch();
        bookingRepository.deleteAllInBatch();
        slotRepository.deleteAllInBatch();
        slotRepository.saveAll(seeds.stream()
                .map(slot -> new Slot(slot.id(), slot.provider(), slot.start(), slot.end()))
                .toList());
        log.info("Reset clinic data: slots={}", seeds.size());
    }

    public Map<String, Object> history(String email, String limitValue, String cursor) {

        locks.lockClinicDataShared();
        email = normalizeEmail(email);
        int limit = parseLimit(limitValue);
        Instant afterCreatedAt = null;
        String afterId = null;

        if (cursor != null) {
            try {
                if (cursor.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
                    throw new IllegalArgumentException();
                }

                JsonNode parts = objectMapper.readTree(Base64.getDecoder().decode(cursor));
                if (!parts.isArray() || parts.size() != 3 || !parts.get(0).asString().equals(email)) {
                    throw new IllegalArgumentException();
                }

                afterCreatedAt = Instant.parse(parts.get(1).asString());
                afterId = parts.get(2).asString();
                log.info("After id: {}", afterId); //TODO: Delete this line
                UUID.fromString(afterId);

            } catch (Exception ex) {
                throw new ApiFailure(400, "invalid cursor");
            }
        }

        PageRequest pageRequest = PageRequest.of(0, limit + 1);
        List<Booking> rows = afterCreatedAt == null
                ? bookingRepository.findHistoryFirst(email, pageRequest)
                : bookingRepository.findHistoryAfter(email, afterCreatedAt, afterId, pageRequest);

        List<Booking> page = rows;
        String next = null;

        if (rows.size() > limit) {
            page = new ArrayList<>(rows.subList(0, limit));
            Booking last = page.get(limit - 1);
            next = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    encode(List.of(email, last.getCreatedAt().toString(), last.getId()))
                            .getBytes(StandardCharsets.UTF_8));
        }

        log.debug("Listed booking history: limit={} hasCursor={} count={}",
                limit, cursor != null, page.size());

        var result = new LinkedHashMap<String, Object>();
        result.put("bookings", page.stream().map(this::toMap).toList());
        result.put("next_cursor", next);
        return result;
    }

    public Outcome create(BookingRequest request, String key) {
        String normalized;
        try {
            normalized = normalizeEmail(request.email());
        } catch (ApiFailure ex) {
            normalized = null;
        }

        final String email = normalized;
        Integer retryAfter = email == null ? null : limiter.attempt(email);
        log.info("Retry after: {}", retryAfter); //TODO: Delete this line

        if (retryAfter != null) {
            log.warn("Booking attempt rate limited: retryAfterSeconds={}", retryAfter);
            return new Outcome(429, encode(Map.of("error", "too many booking attempts")), retryAfter);
        }

        Outcome outcome = transactions.execute(
                tx -> createWithinTransaction(request, key, email)
        );
        assert outcome != null;
        log.debug("Booking request completed: status={}", outcome.statusCode());
        return outcome;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> slots(String provider) {
        locks.lockClinicDataShared();

        List<Slot> slots = provider == null
                ? slotRepository.findAllByOrderByStartAtAscIdAsc()
                : slotRepository.findByProviderOrderByStartAtAscIdAsc(provider);

        Set<String> bookedSlotsIds = new HashSet<>(
                bookingRepository.findSlotIdsByStatus(Booking.CONFIRMED)
        );

        slots.forEach(slot -> slot.setAvailable(!bookedSlotsIds.contains(slot.getId())));

        List<Map<String, Object>> result = slots.stream()
                .map(this::toMap)
                .toList();
        log.debug("Listed slots: provider={} count={}", provider, result.size());
        return Map.of("slots", result);
    }

    public Map<String, Object> cancel(String id) {
        locks.lockClinicDataShared();

        Booking booking = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiFailure(404, "booking not found"));

        if (Booking.CANCELLED.equals(booking.getStatus())) {
            throw new ApiFailure(409, "booking already cancelled");
        }

        booking.setStatus(Booking.CANCELLED);
        log.info("Cancelled booking: id={}", id);
        return toMap(booking);
    }

    private Outcome createWithinTransaction(BookingRequest request, String key, String email) {
        locks.lockClinicDataShared();
        if (key != null && (key.isBlank() || key.length() > MAX_IDEMPOTENCY_KEY_LENGTH)) {
            throw new ApiFailure(400, "invalid Idempotency-Key");
        }

        // For idempotency, serialize the request to a canonical string
        String comparison = encode(request);

        if (key != null) {
            locks.lockIdempotencyKey(key);
            Optional<Idempotency> optionalIdempotency = idempotencyRepository.findById(key);

            if (optionalIdempotency.isPresent()) {
                Idempotency idempotency = optionalIdempotency.get();

                if (!idempotency.getRequestBody().equals(comparison)) {
                    log.debug("Idempotency key reused with a different body");
                    return error(422, "idempotency key reused with different body");
                }

                log.debug("Replaying stored idempotent response: status={}", idempotency.getHttpStatus());
                return new Outcome(idempotency.getHttpStatus(), idempotency.getResponseBody(), null);
            }
        }

        Outcome outcome;
        try {
            outcome = book(request, email);
        } catch (ApiFailure ex) {
            log.debug("Booking rejected: status={} reason={}", ex.getStatus(), ex.getMessage());
            outcome = error(ex.getStatus(), ex.getMessage());
        }
        if (key != null) {
            idempotencyRepository.save(
                    new Idempotency(key, comparison, outcome.statusCode(), outcome.body()));
        }
        return outcome;
    }

    private Outcome book(BookingRequest request, String email) {
        if (email == null) {
            throw new ApiFailure(400, "invalid email");
        }

        String slot = request.slotId();
        if (slot == null || slot.isBlank() || slot.length() > MAX_TEXT_LENGTH) {
            throw new ApiFailure(400, "invalid slot_id");
        }

        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > MAX_TEXT_LENGTH) {
            throw new ApiFailure(400, "invalid name");
        }

        Slot reservedSlot = slotRepository.findByIdForUpdate(slot)
                .orElseThrow(() -> new ApiFailure(404, "slot not found"));

        if (!reservedSlot.getStartAt().isAfter(clock.instant())) {
            throw new ApiFailure(400, "slot has already started");
        }

        if (bookingRepository.existsBySlotIdAndStatus(slot, Booking.CONFIRMED)) {
            throw new ApiFailure(409, "slot already booked");
        }

        String id = UUID.randomUUID().toString();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Booking booking = new Booking(id, slot, name, email, Booking.CONFIRMED, now);
        bookingRepository.save(booking);
        log.info("Created booking: id={} slot={}", id, slot);

        return new Outcome(201, encode(toMap(booking)), null);
    }

    private int parseLimit(String limitValue) {
        try {
            if (limitValue != null && !limitValue.matches("[0-9]+")) {
                throw new NumberFormatException();
            }
            int limit = limitValue == null ? pagination.defaultLimit() : Integer.parseInt(limitValue);
            if (limit < pagination.minLimit() || limit > pagination.maxLimit()) {
                throw invalidLimitError();
            }
            return limit;
        } catch (NumberFormatException ex) {
            throw invalidLimitError();
        }
    }

    private ApiFailure invalidLimitError() {
        return new ApiFailure(400, String.format(INVALID_LIMIT_ERROR,
                pagination.minLimit(), pagination.maxLimit()));
    }

    private Map<String, Object> toMap(Slot slot) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", slot.getId());
        result.put("provider", slot.getProvider());
        result.put("start", slot.getStartAt().toString());
        result.put("end", slot.getEndAt().toString());
        result.put("available", slot.isAvailable());
        return result;
    }

    private Map<String, Object> toMap(Booking booking) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("id", booking.getId());
        result.put("slot_id", booking.getSlotId());
        result.put("name", booking.getName());
        result.put("email", booking.getEmail());
        result.put("status", booking.getStatus());
        result.put("created_at", booking.getCreatedAt().toString());
        return result;
    }

    private String encode(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank() || email.length() > MAX_EMAIL_LENGTH) {
            throw new ApiFailure(400, "invalid email");
        }
        email = email.trim().toLowerCase(Locale.ROOT);
        if (!validator.validate(new Address(email)).isEmpty() || !email.contains("@")) {
            throw new ApiFailure(400, "invalid email");
        }
        return email;
    }

    private Outcome error(int status, String message) {
        return new Outcome(status, encode(Map.of("error", message)), null);
    }
}
