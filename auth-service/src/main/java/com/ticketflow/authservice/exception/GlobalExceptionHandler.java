package com.ticketflow.authservice.exception;

import com.ticketflow.authservice.dto.ErrorResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler — centralized exception-to-HTTP-response mapping.
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody.
 * Applies to every @RestController in the application context.
 * Spring routes unhandled exceptions here before the default /error handler.
 *
 * Rules enforced here:
 *   1. No stack traces in responses — ever.
 *   2. No internal class names in responses.
 *   3. No SQL or Hibernate details in responses.
 *   4. Log WARN for 4xx (client errors), ERROR for 5xx (our errors).
 *   5. Every response uses the ErrorResponseDto envelope.
 *   6. traceId from MDC connects response to Jaeger trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // =========================================================================
    // 400 BAD REQUEST — Bean Validation failures
    // =========================================================================

    /**
     * Handles @Valid failures on @RequestBody DTOs.
     *
     * Collects ALL field errors in one response — not just the first.
     * Format: "fieldName: message; fieldName: message"
     *
     * Example: "email: Email must be a valid email address;
     *            password: Password must be 8-72 characters..."
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationException(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("event=VALIDATION_FAILED errors={}", message);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "VALIDATION_ERROR",
                        message,
                        getTraceId()
                ));
    }

    // =========================================================================
    // 409 CONFLICT — Business rule: duplicate email
    // =========================================================================

    /**
     * Handles explicit duplicate email detection in AuthService.
     * This fires when the service-level check catches the duplicate
     * before the DB INSERT attempt.
     */
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponseDto> handleDuplicateEmailException(
            DuplicateEmailException ex) {

        log.warn("event=DUPLICATE_EMAIL_REJECTED message={}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponseDto.of(
                        HttpStatus.CONFLICT.value(),
                        "DUPLICATE_EMAIL",
                        ex.getMessage(),
                        getTraceId()
                ));
    }

    // =========================================================================
    // 409 CONFLICT — Race condition: two concurrent registrations hit DB UNIQUE
    // =========================================================================

    /**
     * Handles the race condition where two concurrent registration requests
     * both pass the service-level existsByEmail check, then race to INSERT.
     * The second one hits the UNIQUE constraint on auth_users.email and
     * PostgreSQL throws a constraint violation which Spring translates to
     * DataIntegrityViolationException.
     *
     * MUST be declared BEFORE the DataAccessException handler below.
     * DataIntegrityViolationException extends DataAccessException —
     * Spring picks the most specific matching handler, but explicit
     * ordering is clearer and safer.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex) {

        // Log the full cause for internal debugging — never send to client.
        log.warn("event=DB_CONSTRAINT_VIOLATION cause={}", ex.getMostSpecificCause().getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponseDto.of(
                        HttpStatus.CONFLICT.value(),
                        "DUPLICATE_EMAIL",
                        "An account with this email already exists.",
                        getTraceId()
                ));
    }

    // =========================================================================
    // 503 SERVICE UNAVAILABLE — Database connectivity failures
    // =========================================================================

    /**
     * Handles all other Spring Data / JDBC exceptions:
     *   - Connection pool exhausted
     *   - Database unreachable
     *   - Query timeout
     *
     * These are transient — the DB will recover. 503 tells the client
     * to retry. A Retry-After header would be ideal here; added when
     * we wire circuit breaker metrics to know the estimated recovery time.
     *
     * DataIntegrityViolationException is caught above — this handler
     * never sees constraint violations.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponseDto> handleDataAccessException(
            DataAccessException ex) {

        // ERROR level — this is our infrastructure, not the client's fault.
        log.error("event=DB_ACCESS_FAILURE cause={}", ex.getMostSpecificCause().getMessage());

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponseDto.of(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "SERVICE_UNAVAILABLE",
                        "The service is temporarily unavailable. Please try again shortly.",
                        getTraceId()
                ));
    }

    // =========================================================================
    // 500 INTERNAL SERVER ERROR — Catch-all for anything unexpected
    // =========================================================================

    /**
     * Catches everything that no other handler matched.
     *
     * This is the safety net. If this fires in production, it means
     * we have an unhandled exception type that needs its own handler.
     * The ERROR log here should trigger an alert.
     *
     * The message returned to the client is deliberately generic.
     * The actual exception is logged with full detail for internal debugging.
     * Clients never see exception class names or stack traces.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpectedException(Exception ex) {

        log.error("event=UNHANDLED_EXCEPTION type={} message={}",
                ex.getClass().getName(), ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDto.of(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred. Please try again.",
                        getTraceId()
                ));
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    /**
     * Reads the trace ID from MDC.
     *
     * OpenTelemetry auto-instrumentation injects "trace_id" into MDC
     * on every incoming request. This connects the error response to
     * the full distributed trace in Jaeger.
     *
     * Falls back to "unavailable" if OTel is not yet active (e.g. during
     * integration tests without the OTel agent). Never returns null.
     */
    private String getTraceId() {
        String traceId = MDC.get("trace_id");
        return traceId != null ? traceId : "unavailable";
    }
}