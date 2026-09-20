package com.ticketflow.authservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * ErrorResponseDto — standard error envelope for all API error responses.
 *
 * Contract (system design Section 13):
 * {
 *   "status":    400,
 *   "error":     "VALIDATION_ERROR",
 *   "message":   "Email is required",
 *   "timestamp": "2025-09-11T10:30:00.000Z",
 *   "traceId":   "4bf92f3577b34da6"
 * }
 *
 * Every error response from auth-service uses this shape.
 * Clients can rely on this contract unconditionally.
 */
public record ErrorResponseDto(

        // HTTP status code — redundant with the response status line but
        // included in the body for clients that cannot read HTTP headers
        // (e.g. some mobile HTTP client abstractions).
        int status,

        // Machine-readable error code — clients switch on this, not on message.
        // message is for humans. error is for code.
        String error,

        // Human-readable description — safe to display to end users.
        // Never includes: stack traces, class names, SQL, internal IDs.
        String message,

        // UTC timestamp of when the error occurred.
        // ISO-8601 format: "2025-09-11T10:30:00.000Z"
        @JsonFormat(shape = JsonFormat.Shape.STRING,
                pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                timezone = "UTC")
        Instant timestamp,

        // Distributed trace ID injected by OpenTelemetry into MDC.
        // Connects this error response to the full request trace in Jaeger.
        // Client reports this ID when filing a support ticket.
        String traceId

) {
    /**
     * Factory method — builds an ErrorResponseDto with current timestamp.
     * Callers provide status, error code, message, and traceId.
     * Timestamp is always now() — no caller should set it manually.
     */
    public static ErrorResponseDto of(int status, String error,
                                      String message, String traceId) {
        return new ErrorResponseDto(status, error, message, Instant.now(), traceId);
    }
}
