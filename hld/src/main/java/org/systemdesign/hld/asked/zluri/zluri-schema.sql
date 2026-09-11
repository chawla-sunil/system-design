-- =====================================================================
-- Railway Reservation Platform — Concrete SQL Schema (PostgreSQL)
-- Companion to zluri-solution-hld.md
--
-- Design notes:
--  * Route = ordered RouteStop rows (seq 0..n). Segment Li is between
--    seq=i and seq=i+1. A journey src_seq..dst_seq occupies segments
--    [src_seq, dst_seq).
--  * Availability is tracked PER SEGMENT (segment_availability) and the
--    concrete berth is tracked with a PER-SEAT segment bitmask
--    (seat_inventory.occupied_bitmask).
--  * All inventory for a (train_id, journey_date) is co-located so a
--    booking is a single-shard ACID transaction (no 2PC).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. Enums
-- ---------------------------------------------------------------------
CREATE TYPE travel_class AS ENUM ('SL', '3A', '2A', '1A', '2S', 'CC');
CREATE TYPE pnr_status   AS ENUM ('HOLD', 'CONFIRMED', 'CANCELLED', 'EXPIRED', 'WAITLIST');
CREATE TYPE berth_pref   AS ENUM ('LOWER', 'MIDDLE', 'UPPER', 'SIDE_LOWER', 'SIDE_UPPER', 'ANY');

-- =====================================================================
-- 1. STATIC / METADATA  (rarely changes — cache heavily, read replicas)
-- =====================================================================

CREATE TABLE station (
    station_id   BIGINT       PRIMARY KEY,
    code         VARCHAR(10)  NOT NULL UNIQUE,     -- e.g. NDLS, BCT
    name         VARCHAR(128) NOT NULL,
    city         VARCHAR(128),
    lat          DOUBLE PRECISION,
    lon          DOUBLE PRECISION
);

CREATE TABLE train (
    train_id        BIGINT       PRIMARY KEY,
    number          VARCHAR(10)  NOT NULL UNIQUE,  -- e.g. 12951
    name            VARCHAR(128) NOT NULL,
    -- 7-bit mask, bit0=Mon .. bit6=Sun; which weekdays the train departs origin
    run_days_mask   SMALLINT     NOT NULL CHECK (run_days_mask BETWEEN 0 AND 127)
);

-- Ordered stops that define the route. seq is 0-based from origin.
CREATE TABLE route_stop (
    train_id     BIGINT   NOT NULL REFERENCES train(train_id),
    seq          SMALLINT NOT NULL,               -- 0..n
    station_id   BIGINT   NOT NULL REFERENCES station(station_id),
    arr_time     TIME,                            -- NULL at origin
    dep_time     TIME,                            -- NULL at terminus
    day_offset   SMALLINT NOT NULL DEFAULT 0,     -- days from origin departure
    distance_km  INTEGER  NOT NULL DEFAULT 0,     -- cumulative from origin
    PRIMARY KEY (train_id, seq),
    UNIQUE (train_id, station_id)
);
CREATE INDEX idx_route_stop_station ON route_stop(station_id, train_id, seq);

-- Precomputed search index: which trains connect an ordered station pair.
-- (src_seq < dst_seq guaranteed at insert time.)
CREATE TABLE station_pair_train (
    src_station_id BIGINT   NOT NULL REFERENCES station(station_id),
    dst_station_id BIGINT   NOT NULL REFERENCES station(station_id),
    train_id       BIGINT   NOT NULL REFERENCES train(train_id),
    src_seq        SMALLINT NOT NULL,
    dst_seq        SMALLINT NOT NULL,
    PRIMARY KEY (src_station_id, dst_station_id, train_id),
    CHECK (src_seq < dst_seq)
);
CREATE INDEX idx_spt_lookup ON station_pair_train(src_station_id, dst_station_id);

-- Coaches per train+class and how many seats each coach has.
CREATE TABLE coach (
    coach_id    BIGINT       PRIMARY KEY,
    train_id    BIGINT       NOT NULL REFERENCES train(train_id),
    class       travel_class NOT NULL,
    code        VARCHAR(8)   NOT NULL,             -- e.g. B1, A1, S4
    seat_count  SMALLINT     NOT NULL CHECK (seat_count > 0),
    UNIQUE (train_id, code)
);
CREATE INDEX idx_coach_train_class ON coach(train_id, class);

-- Distance/segment based fare rules. Resolve price for (train,class,src,dst).
CREATE TABLE fare_rule (
    train_id     BIGINT       NOT NULL REFERENCES train(train_id),
    class        travel_class NOT NULL,
    from_seq     SMALLINT     NOT NULL,
    to_seq       SMALLINT     NOT NULL,
    price_cents  INTEGER      NOT NULL CHECK (price_cents >= 0),
    PRIMARY KEY (train_id, class, from_seq, to_seq),
    CHECK (from_seq < to_seq)
);

-- =====================================================================
-- 2. INVENTORY  (source of truth, hot — shard by (train_id, journey_date))
-- =====================================================================

-- 2a. Per-segment availability counter. One row per
--     (train, date, class, segment). Powers Search and the booking guard.
CREATE TABLE segment_availability (
    train_id        BIGINT       NOT NULL,
    journey_date    DATE         NOT NULL,
    class           travel_class NOT NULL,
    segment_idx     SMALLINT     NOT NULL,          -- Li, 0-based
    total_count     SMALLINT     NOT NULL,          -- = sum(coach.seat_count) for class
    available_count SMALLINT     NOT NULL,
    PRIMARY KEY (train_id, journey_date, class, segment_idx),
    -- Hard invariant: never oversell a segment.
    CHECK (available_count >= 0 AND available_count <= total_count)
);
-- Covering index for MIN() availability scan over a segment range.
CREATE INDEX idx_segavail_range
    ON segment_availability(train_id, journey_date, class, segment_idx)
    INCLUDE (available_count);

-- 2b. Per-seat segment bitmask ledger. Tracks the CONCRETE berth.
--     Bit i of occupied_bitmask set => segment Li is occupied on this seat.
--     A seat is bookable for [i,j) iff (occupied_bitmask & requestMask) = 0.
--     BIT VARYING supports routes longer than 64 segments.
CREATE TABLE seat_inventory (
    train_id         BIGINT      NOT NULL,
    journey_date     DATE        NOT NULL,
    coach_id         BIGINT      NOT NULL REFERENCES coach(coach_id),
    seat_no          SMALLINT    NOT NULL,
    berth            berth_pref  NOT NULL DEFAULT 'ANY',
    occupied_bitmask BIT VARYING NOT NULL,          -- length = number of segments
    PRIMARY KEY (train_id, journey_date, coach_id, seat_no)
);
CREATE INDEX idx_seatinv_lookup ON seat_inventory(train_id, journey_date, coach_id);

-- =====================================================================
-- 3. BOOKING / PNR
-- =====================================================================

CREATE TABLE pnr (
    pnr_id          BIGINT       PRIMARY KEY,       -- or human PNR string
    user_id         BIGINT       NOT NULL,
    train_id        BIGINT       NOT NULL REFERENCES train(train_id),
    journey_date    DATE         NOT NULL,
    class           travel_class NOT NULL,
    src_seq         SMALLINT     NOT NULL,
    dst_seq         SMALLINT     NOT NULL,
    status          pnr_status   NOT NULL,
    fare_cents      INTEGER      NOT NULL CHECK (fare_cents >= 0),
    passenger_count SMALLINT     NOT NULL CHECK (passenger_count > 0),
    idempotency_key VARCHAR(80)  NOT NULL,
    hold_expiry     TIMESTAMPTZ,                    -- set while status=HOLD
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (src_seq < dst_seq),
    -- Idempotency: a retried request maps to the same PNR.
    UNIQUE (idempotency_key)
);
CREATE INDEX idx_pnr_user       ON pnr(user_id, created_at DESC);
-- Reaper: quickly find expired holds.
CREATE INDEX idx_pnr_hold_expiry ON pnr(status, hold_expiry) WHERE status = 'HOLD';

CREATE TABLE passenger (
    pnr_id      BIGINT      NOT NULL REFERENCES pnr(pnr_id) ON DELETE CASCADE,
    idx         SMALLINT    NOT NULL,               -- 1..passenger_count
    name        VARCHAR(128) NOT NULL,
    age         SMALLINT    NOT NULL CHECK (age >= 0),
    coach_id    BIGINT      REFERENCES coach(coach_id),  -- allocated seat
    seat_no     SMALLINT,
    berth_pref  berth_pref  NOT NULL DEFAULT 'ANY',
    PRIMARY KEY (pnr_id, idx)
);

-- Transactional outbox for reliable event publishing (BookingConfirmed, etc.)
CREATE TABLE outbox_event (
    event_id     BIGINT       PRIMARY KEY,
    aggregate_id BIGINT       NOT NULL,             -- e.g. pnr_id
    type         VARCHAR(64)  NOT NULL,             -- BookingConfirmed / HoldReleased
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ                        -- NULL until relay ships it
);
CREATE INDEX idx_outbox_unpublished ON outbox_event(created_at) WHERE published_at IS NULL;

-- =====================================================================
-- 4. CORE OPERATIONS (illustrative parameterized SQL)
-- =====================================================================

-- 4.1 SEARCH: availability for a (train, date, class) over journey [:src,:dst)
--     = MIN(available_count) across the requested segments.
-- SELECT MIN(available_count) AS available_seats
-- FROM   segment_availability
-- WHERE  train_id = :train AND journey_date = :date AND class = :class
--   AND  segment_idx >= :src_seq AND segment_idx < :dst_seq;

-- 4.2 BOOKING — guarded segment decrement (anti-oversell).
--     Runs inside one transaction on the (train,date) shard.
--     rows_affected MUST equal (:dst_seq - :src_seq); else ROLLBACK.
-- BEGIN;
-- UPDATE segment_availability
-- SET    available_count = available_count - :n
-- WHERE  train_id = :train AND journey_date = :date AND class = :class
--   AND  segment_idx >= :src_seq AND segment_idx < :dst_seq
--   AND  available_count >= :n;                     -- guard: never negative
-- -- if GET DIAGNOSTICS row_count <> (:dst_seq - :src_seq) then ROLLBACK.
--
-- 4.3 Allocate a concrete seat whose bits are all free for the journey.
--     requestMask has bits [src_seq, dst_seq) set (built app-side).
-- SELECT coach_id, seat_no
-- FROM   seat_inventory
-- WHERE  train_id = :train AND journey_date = :date
--   AND  coach_id IN (SELECT coach_id FROM coach WHERE train_id = :train AND class = :class)
--   AND  (occupied_bitmask & :request_mask) = :zero_mask   -- no overlap
-- LIMIT  :n
-- FOR UPDATE SKIP LOCKED;                            -- avoid contention
--
-- UPDATE seat_inventory
-- SET    occupied_bitmask = occupied_bitmask | :request_mask
-- WHERE  train_id = :train AND journey_date = :date
--   AND  coach_id = :coach AND seat_no = :seat;
--
-- INSERT INTO pnr(...) VALUES (..., status = 'HOLD', hold_expiry = now() + interval '10 min');
-- INSERT INTO passenger(...) VALUES (...);
-- INSERT INTO outbox_event(type, ...) VALUES ('BookingReserved', ...);
-- COMMIT;

-- 4.4 CONFIRM after successful payment.
-- UPDATE pnr SET status = 'CONFIRMED', hold_expiry = NULL, updated_at = now()
-- WHERE  pnr_id = :pnr AND status = 'HOLD';
-- INSERT INTO outbox_event(type, ...) VALUES ('BookingConfirmed', ...);

-- 4.5 REAPER: release expired holds (idempotent, batched).
-- BEGIN;
-- WITH expired AS (
--   SELECT pnr_id, src_seq, dst_seq, class, train_id, journey_date, passenger_count
--   FROM   pnr
--   WHERE  status = 'HOLD' AND hold_expiry < now()
--   FOR UPDATE SKIP LOCKED
--   LIMIT  100
-- )
-- UPDATE segment_availability sa
-- SET    available_count = available_count + e.passenger_count
-- FROM   expired e
-- WHERE  sa.train_id = e.train_id AND sa.journey_date = e.journey_date
--   AND  sa.class = e.class
--   AND  sa.segment_idx >= e.src_seq AND sa.segment_idx < e.dst_seq;
-- -- also clear the freed bits in seat_inventory and set pnr.status='EXPIRED'.
-- COMMIT;
