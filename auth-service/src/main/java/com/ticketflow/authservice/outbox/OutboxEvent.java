package com.ticketflow.authservice.outbox;
import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(
                        name = "idx_outbox_events_pending",
                        columnList = "created_at"
                )
        }
)
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 26)
    private String id;

    // The ULID of the entity this event is about (e.g. AuthUser.id)
    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 26)
    private String aggregateId;

    // The entity type (e.g. "AuthUser") — for monitoring and routing
    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 50)
    private String aggregateType;

    // The event name — matches the Kafka topic naming convention
    // e.g. "user.registered"
    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    // JSON payload — the actual Kafka message body
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    // Which Kafka topic to publish to
    @Column(name = "kafka_topic", nullable = false, updatable = false, length = 255)
    private String kafkaTopic;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Null until successfully published
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    // Stores the last exception message if publish failed — for ops debugging
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @PrePersist
    protected void onCreate() {
        this.id = UlidCreator.getMonotonicUlid().toString();
        this.createdAt = Instant.now();
        this.status = "PENDING";
        this.retryCount = 0;
    }

    // =========================================================================
    // STATIC FACTORY — the only way to create an OutboxEvent.
    // Encapsulates construction so callers cannot create a half-built event.
    // =========================================================================

    public static OutboxEvent of(
            String aggregateId,
            String aggregateType,
            String eventType,
            String kafkaTopic,
            String payload) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateId = aggregateId;
        event.aggregateType = aggregateType;
        event.eventType = eventType;
        event.kafkaTopic = kafkaTopic;
        event.payload = payload;
        return event;
    }

    // =========================================================================
    // GETTERS — full set for poller access
    // SETTERS — only for mutable fields (status, publishedAt, retryCount, lastError)
    // =========================================================================

    public String getId() { return id; }
    public String getAggregateId() { return aggregateId; }
    public String getAggregateType() { return aggregateType; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public String getKafkaTopic() { return kafkaTopic; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getRetryCount() { return retryCount; }
    public String getLastError() { return lastError; }

    public void setStatus(String status) { this.status = status; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
