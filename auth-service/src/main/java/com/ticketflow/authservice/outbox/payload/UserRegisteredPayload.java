package com.ticketflow.authservice.outbox.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * UserRegisteredPayload — typed representation of the user.registered event body.
 *
 * This record is serialized to JSON by ObjectMapper and stored in
 * outbox_events.payload. The outbox poller reads the raw JSON string
 * and forwards it as the Kafka message body verbatim.
 *
 * user-service deserializes this same JSON back into an equivalent record
 * on the consumer side. The field names here are the contract between
 * auth-service (producer) and user-service (consumer).
 *
 * Why a typed record instead of a Map or hand-built string:
 *   - Compile-time guarantee that all required fields are present
 *   - ObjectMapper handles escaping — no injection risk from user input
 *   - user-service can deserialize to the same record shape
 *   - Schema changes are a compile error, not a runtime surprise
 *
 * @JsonProperty: makes the JSON contract explicit in code.
 * If we ever rename a Java field, the JSON key stays stable.
 */
public record UserRegisteredPayload(

        @JsonProperty("userId")
        String userId,

        @JsonProperty("email")
        String email,

        @JsonProperty("firstName")
        String firstName,

        @JsonProperty("lastName")
        String lastName
) {}