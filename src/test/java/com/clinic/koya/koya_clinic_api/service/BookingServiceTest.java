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
import com.clinic.koya.koya_clinic_api.repository.AdvisoryLockRepository;
import com.clinic.koya.koya_clinic_api.repository.BookingAttemptRepository;
import com.clinic.koya.koya_clinic_api.repository.BookingRepository;
import com.clinic.koya.koya_clinic_api.repository.IdempotencyRepository;
import com.clinic.koya.koya_clinic_api.repository.SlotRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final String EMAIL = "ada@example.com";

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @Mock
    private BookingAttemptRepository attemptRepository;

    @Mock
    private AdvisoryLockRepository locks;

    @Mock
    private RateLimiter limiter;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private BookingService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(limiter.attempt(anyString())).thenReturn(null);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        service = new BookingService(
                new PaginationProperties(1, 50, 10),
                slotRepository,
                bookingRepository,
                idempotencyRepository,
                attemptRepository,
                locks,
                objectMapper,
                clock,
                limiter,
                validator,
                new TransactionTemplate(txManager)
        );
    }

    @Test
    void nowReturnsClockInstant() {
        assertThat(service.now()).isEqualTo(NOW);
    }

    @Test
    void getReturnsMappedBooking() {
        Booking booking = booking("b1", "s1", Booking.CONFIRMED, NOW);
        when(bookingRepository.findById("b1")).thenReturn(Optional.of(booking));

        Map<String, Object> result = service.get("b1");

        assertThat(result)
                .containsEntry("id", "b1")
                .containsEntry("slot_id", "s1")
                .containsEntry("name", "Ada")
                .containsEntry("email", EMAIL)
                .containsEntry("status", Booking.CONFIRMED)
                .containsEntry("created_at", NOW.toString());
        verify(locks).lockClinicDataShared();
    }

    @Test
    void getThrowsNotFoundWhenMissing() {
        when(bookingRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("missing"))
                .isInstanceOf(ApiFailure.class)
                .extracting(ex -> ((ApiFailure) ex).getStatus())
                .isEqualTo(404);
    }

    @Test
    void resetReplacesAllDataWithValidatedSeeds() {
        SlotDefinition first = new SlotDefinition("s1", "Dr Ada", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        SlotDefinition second = new SlotDefinition("s2", "Dr Bola", NOW.plusSeconds(10800), NOW.plusSeconds(14400));

        service.reset(new ResetRequest(List.of(first, second)));

        verify(locks).lockClinicDataExclusive();
        verify(idempotencyRepository).deleteAllInBatch();
        verify(attemptRepository).deleteAllInBatch();
        verify(bookingRepository).deleteAllInBatch();
        verify(slotRepository).deleteAllInBatch();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Slot>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(slotRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(Slot::getId, Slot::getProvider, Slot::getStartAt, Slot::getEndAt)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("s1", "Dr Ada", first.start(), first.end()),
                        org.assertj.core.groups.Tuple.tuple("s2", "Dr Bola", second.start(), second.end())
                );
    }

    @Test
    void resetRejectsNullRequest() {
        assertBadRequest(() -> service.reset(null));
    }

    @Test
    void resetRejectsNullSlots() {
        assertBadRequest(() -> service.reset(new ResetRequest(null)));
    }

    @Test
    void resetRejectsDuplicateSlotIds() {
        SlotDefinition slot = new SlotDefinition("s1", "Dr Ada", NOW.plusSeconds(3600), NOW.plusSeconds(7200));

        assertBadRequest(() -> service.reset(new ResetRequest(List.of(slot, slot))));
    }

    @Test
    void resetRejectsInvalidTimeRange() {
        SlotDefinition slot = new SlotDefinition("s1", "Dr Ada", NOW.plusSeconds(7200), NOW.plusSeconds(3600));

        assertBadRequest(() -> service.reset(new ResetRequest(List.of(slot))));
    }

    @Test
    void slotsMarksBookedSlotsUnavailable() {
        Slot first = slot("s1", "Dr Ada", NOW.plusSeconds(3600));
        Slot second = slot("s2", "Dr Bola", NOW.plusSeconds(7200));
        when(slotRepository.findAllByOrderByStartAtAscIdAsc()).thenReturn(List.of(first, second));
        when(bookingRepository.findSlotIdsByStatus(Booking.CONFIRMED)).thenReturn(List.of("s1"));

        Map<String, Object> result = service.slots(null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots = (List<Map<String, Object>>) result.get("slots");
        assertThat(slots).hasSize(2);
        assertThat(slots.get(0)).containsEntry("id", "s1").containsEntry("available", false);
        assertThat(slots.get(1)).containsEntry("id", "s2").containsEntry("available", true);
        verify(slotRepository).findAllByOrderByStartAtAscIdAsc();
    }

    @Test
    void slotsFiltersByProvider() {
        Slot slot = slot("s1", "Dr Ada", NOW.plusSeconds(3600));
        when(slotRepository.findByProviderOrderByStartAtAscIdAsc("Dr Ada")).thenReturn(List.of(slot));
        when(bookingRepository.findSlotIdsByStatus(Booking.CONFIRMED)).thenReturn(List.of());

        Map<String, Object> result = service.slots("Dr Ada");

        assertThat((List<?>) result.get("slots")).hasSize(1);
        verify(slotRepository).findByProviderOrderByStartAtAscIdAsc("Dr Ada");
        verify(slotRepository, never()).findAllByOrderByStartAtAscIdAsc();
    }

    @Test
    void cancelMarksConfirmedBookingCancelled() {
        Booking booking = booking("b1", "s1", Booking.CONFIRMED, NOW);
        when(bookingRepository.findByIdForUpdate("b1")).thenReturn(Optional.of(booking));

        Map<String, Object> result = service.cancel("b1");

        assertThat(result).containsEntry("status", Booking.CANCELLED);
        assertThat(booking.getStatus()).isEqualTo(Booking.CANCELLED);
    }

    @Test
    void cancelRejectsAlreadyCancelledBooking() {
        when(bookingRepository.findByIdForUpdate("b1"))
                .thenReturn(Optional.of(booking("b1", "s1", Booking.CANCELLED, NOW)));

        assertThatThrownBy(() -> service.cancel("b1"))
                .isInstanceOf(ApiFailure.class)
                .extracting(ex -> ((ApiFailure) ex).getStatus())
                .isEqualTo(409);
    }

    @Test
    void cancelRejectsMissingBooking() {
        when(bookingRepository.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel("missing"))
                .isInstanceOf(ApiFailure.class)
                .extracting(ex -> ((ApiFailure) ex).getStatus())
                .isEqualTo(404);
    }

    @Test
    void historyReturnsFirstPageWithoutCursor() {
        Booking first = booking("b1", "s1", Booking.CONFIRMED, NOW.minusSeconds(60));
        Booking second = booking("b2", "s2", Booking.CONFIRMED, NOW);
        when(bookingRepository.findHistoryFirst(eq(EMAIL), any(PageRequest.class)))
                .thenReturn(List.of(first, second));

        Map<String, Object> result = service.history(" Ada@Example.com ", null, null);

        assertThat((List<?>) result.get("bookings")).hasSize(2);
        assertThat(result.get("next_cursor")).isNull();

        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(bookingRepository).findHistoryFirst(eq(EMAIL), pageCaptor.capture());
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(11);
        assertThat(pageCaptor.getValue().getPageNumber()).isZero();
    }

    @Test
    void historyReturnsCursorWhenMoreRowsExist() {
        Booking first = booking("b1", "s1", Booking.CONFIRMED, NOW.minusSeconds(60));
        Booking second = booking("b2", "s2", Booking.CONFIRMED, NOW);
        when(bookingRepository.findHistoryFirst(eq(EMAIL), any(PageRequest.class)))
                .thenReturn(List.of(first, second));

        Map<String, Object> result = service.history(EMAIL, "1", null);

        assertThat((List<?>) result.get("bookings")).hasSize(1);
        String cursor = (String) result.get("next_cursor");
        assertThat(cursor).isNotBlank();
        String decoded = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
        assertThat(decoded).contains(EMAIL).contains(first.getCreatedAt().toString()).contains("b1");
    }

    @Test
    void historyUsesCursorToLoadNextPage() {
        Instant createdAt = NOW.minusSeconds(60);
        String cursor = Base64.getEncoder().encodeToString(
                ("[\"" + EMAIL + "\",\"" + createdAt + "\",\"" + UUID.randomUUID() + "\"]")
                        .getBytes(StandardCharsets.UTF_8));
        Booking booking = booking("b2", "s2", Booking.CONFIRMED, NOW);
        when(bookingRepository.findHistoryAfter(eq(EMAIL), eq(createdAt), anyString(), any(PageRequest.class)))
                .thenReturn(List.of(booking));

        Map<String, Object> result = service.history(EMAIL, "10", cursor);

        assertThat((List<?>) result.get("bookings")).hasSize(1);
        verify(bookingRepository, never()).findHistoryFirst(anyString(), any(PageRequest.class));
    }

    @Test
    void historyRejectsInvalidCursors() {
        String wrongShape = encodeCursor("[\"only-one\"]");
        String wrongEmail = encodeCursor("[\"other@example.com\",\"2026-01-01T10:00:00Z\",\""
                + UUID.randomUUID() + "\"]");
        String badTimestamp = encodeCursor("[\"" + EMAIL + "\",\"not-a-date\",\""
                + UUID.randomUUID() + "\"]");
        String badId = encodeCursor("[\"" + EMAIL + "\",\"2026-01-01T10:00:00Z\",\"not-a-uuid\"]");
        String base64Garbage = Base64.getEncoder().encodeToString("not json".getBytes(StandardCharsets.UTF_8));

        for (String cursor : List.of(wrongShape, wrongEmail, badTimestamp, badId, base64Garbage)) {
            assertBadRequest(() -> service.history(EMAIL, "1", cursor));
        }
    }

    @Test
    void historyRejectsOversizedCursor() {
        assertBadRequest(() -> service.history(EMAIL, "1", "a".repeat(2001)));
    }

    @Test
    void historyRejectsInvalidLimits() {
        for (String limit : List.of("0", "51", "abc", "-1", "1.5", " 1")) {
            assertThatThrownBy(() -> service.history(EMAIL, limit, null))
                    .isInstanceOf(ApiFailure.class)
                    .hasMessage("limit must be an integer from 1 to 50");
        }
    }

    @Test
    void historyRejectsInvalidEmail() {
        assertBadRequest(() -> service.history("not-an-email", null, null));
        assertBadRequest(() -> service.history("", null, null));
        assertBadRequest(() -> service.history(null, null, null));
    }

    @Test
    void createBooksAvailableFutureSlot() {
        Slot slot = slot("s1", "Dr Ada", NOW.plusSeconds(3600));
        when(slotRepository.findByIdForUpdate("s1")).thenReturn(Optional.of(slot));
        when(bookingRepository.existsBySlotIdAndStatus("s1", Booking.CONFIRMED)).thenReturn(false);

        Outcome outcome = service.create(new BookingRequest("s1", "  Ada  ", " Ada@Example.com "), null);

        assertThat(outcome.statusCode()).isEqualTo(201);
        assertThat(outcome.retryAfter()).isNull();
        assertThat(outcome.body()).contains("\"slot_id\":\"s1\"")
                .contains("\"name\":\"Ada\"")
                .contains("\"email\":\"" + EMAIL + "\"")
                .contains("\"status\":\"" + Booking.CONFIRMED + "\"");

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getSlotId()).isEqualTo("s1");
        assertThat(captor.getValue().getName()).isEqualTo("Ada");
        assertThat(captor.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(UUID.fromString(captor.getValue().getId())).isNotNull();
    }

    @Test
    void createReturnsTooManyRequestsWhenRateLimited() {
        when(limiter.attempt(EMAIL)).thenReturn(30);

        Outcome outcome = service.create(new BookingRequest("s1", "Ada", EMAIL), null);

        assertThat(outcome.statusCode()).isEqualTo(429);
        assertThat(outcome.retryAfter()).isEqualTo(30);
        assertThat(outcome.body()).contains("too many booking attempts");
        verifyNoInteractions(slotRepository, bookingRepository, idempotencyRepository);
    }

    @Test
    void createRejectsInvalidEmail() {
        Outcome missingAt = service.create(new BookingRequest("s1", "Ada", "invalid"), null);
        Outcome nullEmail = service.create(new BookingRequest("s1", "Ada", null), null);

        assertThat(missingAt.statusCode()).isEqualTo(400);
        assertThat(missingAt.body()).contains("invalid email");
        assertThat(nullEmail.statusCode()).isEqualTo(400);
        assertThat(nullEmail.body()).contains("invalid email");
        verify(limiter, never()).attempt(anyString());
    }

    @Test
    void createRejectsInvalidSlotId() {
        assertThat(service.create(new BookingRequest(null, "Ada", EMAIL), null).statusCode()).isEqualTo(400);
        assertThat(service.create(new BookingRequest("  ", "Ada", EMAIL), null).statusCode()).isEqualTo(400);
    }

    @Test
    void createRejectsInvalidName() {
        assertThat(service.create(new BookingRequest("s1", null, EMAIL), null).statusCode()).isEqualTo(400);
        assertThat(service.create(new BookingRequest("s1", "  ", EMAIL), null).statusCode()).isEqualTo(400);
    }

    @Test
    void createRejectsMissingSlot() {
        when(slotRepository.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        Outcome outcome = service.create(new BookingRequest("missing", "Ada", EMAIL), null);

        assertThat(outcome.statusCode()).isEqualTo(404);
        assertThat(outcome.body()).contains("slot not found");
    }

    @Test
    void createRejectsSlotThatAlreadyStarted() {
        Slot slot = slot("s1", "Dr Ada", NOW.minusSeconds(1));
        when(slotRepository.findByIdForUpdate("s1")).thenReturn(Optional.of(slot));

        Outcome outcome = service.create(new BookingRequest("s1", "Ada", EMAIL), null);

        assertThat(outcome.statusCode()).isEqualTo(400);
        assertThat(outcome.body()).contains("slot has already started");
    }

    @Test
    void createRejectsSlotThatIsAlreadyBooked() {
        Slot slot = slot("s1", "Dr Ada", NOW.plusSeconds(3600));
        when(slotRepository.findByIdForUpdate("s1")).thenReturn(Optional.of(slot));
        when(bookingRepository.existsBySlotIdAndStatus("s1", Booking.CONFIRMED)).thenReturn(true);

        Outcome outcome = service.create(new BookingRequest("s1", "Ada", EMAIL), null);

        assertThat(outcome.statusCode()).isEqualTo(409);
        assertThat(outcome.body()).contains("slot already booked");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createReplaysStoredIdempotentResponse() throws Exception {
        BookingRequest request = new BookingRequest("s1", "Ada", EMAIL);
        String body = objectMapper.writeValueAsString(request);
        when(idempotencyRepository.findById("key-1"))
                .thenReturn(Optional.of(new Idempotency("key-1", body, 201, "{\"id\":\"b1\"}")));

        Outcome outcome = service.create(request, "key-1");

        assertThat(outcome.statusCode()).isEqualTo(201);
        assertThat(outcome.body()).isEqualTo("{\"id\":\"b1\"}");
        verify(locks).lockIdempotencyKey("key-1");
        verifyNoInteractions(slotRepository, bookingRepository);
    }

    @Test
    void createRejectsIdempotencyKeyReusedWithDifferentBody() {
        BookingRequest request = new BookingRequest("s1", "Ada", EMAIL);
        when(idempotencyRepository.findById("key-1"))
                .thenReturn(Optional.of(new Idempotency("key-1", "{\"different\":true}", 201, "{}")));

        Outcome outcome = service.create(request, "key-1");

        assertThat(outcome.statusCode()).isEqualTo(422);
        assertThat(outcome.body()).contains("idempotency key reused with different body");
        verifyNoInteractions(slotRepository, bookingRepository);
    }

    @Test
    void createStoresOutcomeAgainstIdempotencyKey() {
        Slot slot = slot("s1", "Dr Ada", NOW.plusSeconds(3600));
        when(slotRepository.findByIdForUpdate("s1")).thenReturn(Optional.of(slot));
        when(bookingRepository.existsBySlotIdAndStatus("s1", Booking.CONFIRMED)).thenReturn(false);
        when(idempotencyRepository.findById("key-1")).thenReturn(Optional.empty());

        Outcome outcome = service.create(new BookingRequest("s1", "Ada", EMAIL), "key-1");

        assertThat(outcome.statusCode()).isEqualTo(201);
        ArgumentCaptor<Idempotency> captor = ArgumentCaptor.forClass(Idempotency.class);
        verify(idempotencyRepository).save(captor.capture());
        assertThat(captor.getValue().getKey()).isEqualTo("key-1");
        assertThat(captor.getValue().getHttpStatus()).isEqualTo(201);
        assertThat(captor.getValue().getResponseBody()).isEqualTo(outcome.body());
    }

    @Test
    void createStoresErrorOutcomeAgainstIdempotencyKey() {
        when(slotRepository.findByIdForUpdate("missing")).thenReturn(Optional.empty());
        when(idempotencyRepository.findById("key-1")).thenReturn(Optional.empty());

        Outcome outcome = service.create(new BookingRequest("missing", "Ada", EMAIL), "key-1");

        assertThat(outcome.statusCode()).isEqualTo(404);
        ArgumentCaptor<Idempotency> captor = ArgumentCaptor.forClass(Idempotency.class);
        verify(idempotencyRepository).save(captor.capture());
        assertThat(captor.getValue().getHttpStatus()).isEqualTo(404);
    }

    @Test
    void createRejectsInvalidIdempotencyKeys() {
        assertBadRequest(() -> service.create(new BookingRequest("s1", "Ada", EMAIL), "  "));
        assertBadRequest(() -> service.create(new BookingRequest("s1", "Ada", EMAIL), "k".repeat(201)));
    }

    private static Booking booking(String id, String slotId, String status, Instant createdAt) {
        return new Booking(id, slotId, "Ada", EMAIL, status, createdAt);
    }

    private static Slot slot(String id, String provider, Instant start) {
        return new Slot(id, provider, start, start.plusSeconds(1800));
    }

    private static String encodeCursor(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(ApiFailure.class)
                .extracting(ex -> ((ApiFailure) ex).getStatus())
                .isEqualTo(400);
    }
}
