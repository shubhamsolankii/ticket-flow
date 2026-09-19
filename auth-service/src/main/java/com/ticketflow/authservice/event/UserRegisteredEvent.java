package com.ticketflow.authservice.event;

/**
 * Internal Spring application event fired by AuthService after a successful
 * registration DB commit. Consumed by UserRegisteredEventPublisher which
 * sends the Kafka message.
 *
 * This is NOT the Kafka message itself — it is the Spring internal event
 * that triggers the Kafka publish AFTER the DB transaction commits.
 *
 * Record: immutable event payload. Created once, read by the listener.
 */

public record UserRegisteredEvent(
        String userId,
        String email,
        String firstName,
        String lastName
) {}