package com.ticketflow.authservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * OutboxEvent — JPA entity for the outbox_events table.
 *
 * Append-only from the application side. Only the poller
 * mutates status, published_at, retry_count, last_error.
 */
@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_events_pending", columnList = "created_at")
        }
)
public class OutboxEvent {

    // Shared ObjectMapper — thread-safe, expensive to construct.
    // Static final: one instance for all OutboxEvent factory calls.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 26)
    private String id;

    // The ULID of the business entity this event is about (e.g. AuthUser.id)
    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 26)
    private String aggregateId;

    // The entity type — for routing, monitoring, and consumer-side filtering
    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 50)
    private String aggregateType;

    // The event name — matches Kafka topic convention (e.g. "user.registered")
    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    // JSON-serialized payload — the exact bytes sent as the Kafka message body.
    // Serialized from a typed payload object via ObjectMapper in the factory method.
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "kafka_topic", nullable = false, updatable = false, length = 255)
    private String kafkaTopic;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

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
    // STATIC FACTORY
    // The only way to create an OutboxEvent.
    // Takes a typed payload object, serializes it to JSON via ObjectMapper.
    // Throws IllegalArgumentException (unchecked) if serialization fails —
    // which only happens if the payload object contains unmappable types,
    // never for our simple records.
    // =========================================================================

    /**
     * Creates an OutboxEvent from a typed payload object.
     *
     * @param aggregateId   the ULID of the business entity this event is about
     * @param aggregateType the entity class name (e.g. "AuthUser")
     * @param eventType     the event name (e.g. "user.registered")
     * @param kafkaTopic    the Kafka topic to publish to
     * @param payloadObject any serializable object — serialized to JSON here
     */
    public static OutboxEvent of(
            String aggregateId,
            String aggregateType,
            String eventType,
            String kafkaTopic,
            Object payloadObject) {

        String jsonPayload;
        try {
            // ObjectMapper.writeValueAsString() is thread-safe when the
            // ObjectMapper instance is fully configured before use.
            // Our static final instance is configured at class load time.
            jsonPayload = OBJECT_MAPPER.writeValueAsString(payloadObject);
        } catch (JsonProcessingException e) {
            // This is a programmer error — not a runtime data error.
            // If this throws, the payload object type is wrong, not the data.
            throw new IllegalArgumentException(
                    "Failed to serialize outbox payload for event type: " + eventType, e
            );
        }

        OutboxEvent event = new OutboxEvent();
        event.aggregateId = aggregateId;
        event.aggregateType = aggregateType;
        event.eventType = eventType;
        event.kafkaTopic = kafkaTopic;
        event.payload = jsonPayload;
        return event;
    }

    // =========================================================================
    // GETTERS — full set for poller access
    // SETTERS — only mutable fields
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