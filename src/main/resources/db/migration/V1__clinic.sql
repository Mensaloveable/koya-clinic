CREATE TABLE slots
(
    id       text PRIMARY KEY,
    provider text        NOT NULL,
    start_at timestamptz NOT NULL,
    end_at   timestamptz NOT NULL CHECK (end_at > start_at)
);
CREATE INDEX slots_start ON slots (start_at, id);
CREATE TABLE bookings
(
    id         text PRIMARY KEY,
    slot_id    text        NOT NULL REFERENCES slots (id),
    name       text        NOT NULL,
    email      text        NOT NULL,
    status     text        NOT NULL CHECK (status IN ('confirmed', 'cancelled')),
    created_at timestamptz NOT NULL
);
CREATE UNIQUE INDEX one_confirmed_booking_per_slot ON bookings (slot_id) WHERE status = 'confirmed';
CREATE INDEX booking_history ON bookings (email, created_at, id);
CREATE TABLE idempotency
(
    key           text PRIMARY KEY,
    request_body  text    NOT NULL,
    http_status   integer NOT NULL,
    response_body text    NOT NULL
);
CREATE TABLE booking_attempts
(
    id           bigserial PRIMARY KEY,
    email        text        NOT NULL,
    attempted_at timestamptz NOT NULL
);
CREATE INDEX attempts_email_time ON booking_attempts (email, attempted_at);
