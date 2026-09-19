package com.ticketflow.authservice.exception;

/**
 * Thrown when registration is attempted with an email that already exists.
 * GlobalExceptionHandler maps this to HTTP 409 Conflict.
 *
 * Extends RuntimeException — unchecked, does not need to be declared
 * in method signatures. Spring's @Transactional rolls back on unchecked
 * exceptions by default.
 */

public class DuplicateEmailException extends RuntimeException{

    public DuplicateEmailException(String email){
        super("An account with email '" + email + "' already exists.");
    }

}
