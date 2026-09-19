-- V3: Outbox events table
-- Stores domain events that must be published to Kafka.
-- Written atomically with the business data in the same transaction.
-- A separate poller reads PENDING rows and publishes them to Kafka.
--
-- This table solves the dual-write problem: DB write and Kafka publish
-- are no longer two separate operations — the DB write IS the event record.

CREATE TABLE outbox_events (
                               id              VARCHAR(26)     NOT NULL,
                               aggregate_id    VARCHAR(26)     NOT NULL,   -- the userId (ULID) this event is about
                               aggregate_type  VARCHAR(50)     NOT NULL,   -- e.g. 'AuthUser'
                               event_type      VARCHAR(100)    NOT NULL,   -- e.g. 'user.registered'
                               payload         TEXT            NOT NULL,   -- JSON event payload
                               status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
                               kafka_topic     VARCHAR(255)    NOT NULL,   -- target Kafka topic
                               created_at      TIMESTAMPTZ     NOT NULL,
                               published_at    TIMESTAMPTZ,                -- set when successfully published
                               retry_count     INT             NOT NULL DEFAULT 0,
                               last_error      TEXT,                       -- last failure reason, for debugging

                               CONSTRAINT pk_outbox_events PRIMARY KEY (id),
                               CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- THE CRITICAL INDEX:
-- Partial index on PENDING rows only.
-- As events are published and marked PUBLISHED, they leave this index.
-- The poller always scans a tiny index regardless of total table size.
-- Without this, the poller does a full table scan on a table that grows
-- forever — query time degrades linearly with traffic.
CREATE INDEX idx_outbox_events_pending
    ON outbox_events (created_at ASC)
    WHERE status = 'PENDING';

-- Index for monitoring queries: "how many events are stuck in FAILED?"
CREATE INDEX idx_outbox_events_status
    ON outbox_events (status, created_at DESC);