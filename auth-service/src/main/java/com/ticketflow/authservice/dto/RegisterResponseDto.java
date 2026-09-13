package com.ticketflow.authservice.dto;

/**
 * RegisterResponseDto — outbound payload for POST /api/v1/auth/register.
 *
 * Returned as HTTP 201 Created on successful registration.
 *
 * Response JSON:
 * {
 *   "userId":  "01ARZ3NDEKTSV4RRFFQ69G5FAV",
 *   "email":   "aryan@example.com",
 *   "message": "Registration successful. Please verify your email."
 * }
 */
public record RegisterResponseDto(

        /**
         * The ULID assigned to the newly created AuthUser.
         * Client uses this as the canonical user identifier for all
         * subsequent API calls. Never expose the DB primary key directly
         * — ULID is our public-facing identifier by design.
         */
        String userId,

        /**
         * Echo back the registered email for client-side confirmation.
         * Allows the client to display "We've sent a verification email to X"
         * without storing the email separately on the client before this call.
         */
        String email,

        /**
         * Human-readable status message for display purposes only.
         * Never write client logic that branches on this string value.
         * Machine-readable status = HTTP 201 status code.
         */
        String message

) {}