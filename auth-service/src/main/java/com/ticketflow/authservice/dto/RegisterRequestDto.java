package com.ticketflow.authservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * RegisterRequestDto — inbound payload for POST /api/v1/auth/register.
 *
 * Immutable by design (Java record). Validated by Bean Validation before
 * reaching AuthService. AuthService never sees invalid input.
 *
 * Expected JSON:
 * {
 *   "email":     "aryan@example.com",
 *   "password":  "SecurePass1",
 *   "firstName": "Aryan",
 *   "lastName":  "Shah"
 * }
 */
public record RegisterRequestDto(

        @JsonProperty("email")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        /**
         * Password constraints (system design Section 10):
         *   - Minimum 8 characters
         *   - At least one uppercase letter
         *   - At least one digit
         *   - Maximum 72 characters — BCrypt silently truncates beyond 72 bytes.
         *     Capping here makes the contract honest.
         *
         * The regex uses lookaheads:
         *   (?=.*[A-Z]) → at least one uppercase letter anywhere in the string
         *   (?=.*[0-9]) → at least one digit anywhere in the string
         *   .{8,72}     → total length between 8 and 72
         */
        @JsonProperty("password")
        @NotBlank(message = "Password is required")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*[0-9]).{8,72}$",
                message = "Password must be 8–72 characters and contain " +
                        "at least one uppercase letter and one number"
        )
        String password,

        @JsonProperty("firstName")
        @NotBlank(message = "First name is required")
        @Size(min = 1, max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @JsonProperty("lastName")
        @NotBlank(message = "Last name is required")
        @Size(min = 1, max = 100, message = "Last name must not exceed 100 characters")
        String lastName

) {}