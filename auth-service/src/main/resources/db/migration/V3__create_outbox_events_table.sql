-- V3: Outbox events table
-- Written atomically with business data in the same DB transaction.
-- Poller reads PENDING rows and publishes to Kafka independently.

CREATE TABLE outbox_events (
                               id              VARCHAR(26)     NOT NULL,
                               aggregate_id    VARCHAR(26)     NOT NULL,
                               aggregate_type  VARCHAR(50)     NOT NULL,
                               event_type      VARCHAR(100)    NOT NULL,
                               payload         TEXT            NOT NULL,   -- JSON object, serialized by ObjectMapper
                               status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
                               kafka_topic     VARCHAR(255)    NOT NULL,
                               created_at      TIMESTAMPTZ     NOT NULL,
                               published_at    TIMESTAMPTZ,
                               retry_count     INT             NOT NULL DEFAULT 0,
                               last_error      TEXT,

                               CONSTRAINT pk_outbox_events
                                   PRIMARY KEY (id),

                               CONSTRAINT chk_outbox_status
                                   CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- Partial index: only indexes PENDING rows.
-- PUBLISHED and FAILED rows are invisible to this index.
-- The poller scans this index at constant speed regardless of table size.
-- This is non-negotiable at production volume.
CREATE INDEX idx_outbox_events_pending
    ON outbox_events (created_at ASC)
    WHERE status = 'PENDING';

-- Ops monitoring index: "how many events are stuck?"
CREATE INDEX idx_outbox_events_status
    ON outbox_events (status, created_at DESC);