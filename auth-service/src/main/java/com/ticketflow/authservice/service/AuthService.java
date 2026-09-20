package com.ticketflow.authservice.service;

import com.ticketflow.authservice.dto.RegisterRequestDto;
import com.ticketflow.authservice.dto.RegisterResponseDto;
import com.ticketflow.authservice.entity.AuthCredential;
import com.ticketflow.authservice.entity.AuthCredential.CredentialType;
import com.ticketflow.authservice.entity.AuthUser;
import com.ticketflow.authservice.exception.DuplicateEmailException;
import com.ticketflow.authservice.outbox.OutboxEvent;
import com.ticketflow.authservice.outbox.OutboxEventRepository;
import com.ticketflow.authservice.outbox.payload.UserRegisteredPayload;
import com.ticketflow.authservice.repository.AuthCredentialRepository;
import com.ticketflow.authservice.repository.AuthUserRepository;
import org.apache.catalina.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuthService — business logic for the identity bounded context.
 *
 * Feature 1: register() only.
 * All other features add methods here in their respective steps.
 */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String TOPIC_USER_REGISTERED = "user.registered";

    private final AuthUserRepository authUserRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AuthUserRepository authUserRepository,
            AuthCredentialRepository authCredentialRepository,
            OutboxEventRepository outboxEventRepository,
            PasswordEncoder passwordEncoder) {
        this.authUserRepository = authUserRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new user with email and password.
     *
     * Atomicity: auth_users + auth_credentials + outbox_events — all three
     * rows are written in one transaction. PostgreSQL guarantees all succeed
     * or all fail. There is no state where a user exists without an outbox event.
     *
     * The outbox poller (OutboxEventPublisher) reads the outbox row and
     * publishes to Kafka in a completely separate transaction and thread.
     *
     * @param dto validated registration payload from the controller
     * @return RegisterResponseDto — userId, email, confirmation message
     * @throws DuplicateEmailException mapped to HTTP 409 by GlobalExceptionHandler
     */

     @Transactional(isolation = Isolation.READ_COMMITTED)
     public RegisterResponseDto register(RegisterRequestDto dto){
         // Normalize email: lowercase + strip whitespace.
         // Prevents aryan@example.com and Aryan@Example.com being two accounts.
         String email = dto.email().toLowerCase().strip();
         log.info("event=REGISTRATION_ATTEMPT email={}", email);

         if(authUserRepository.existsByEmail(email)){
             log.warn("event=REGISTRATION_DUPLICATE_EMAIL email={}", email);
             throw new DuplicateEmailException(email);
         }

         // ─────────────────────────────────────────────────────────────────
         // Duplicate check at credential level.
         // Handles the future case: user exists via Google OAuth2 but tries
         // to register with the same email via email+password.
         // That is a "add new credential" flow, not a duplicate — but we
         // block it here until that feature is explicitly built.
         // ─────────────────────────────────────────────────────────────────

         if(authCredentialRepository.existsByEmailAndCredentialType(email, CredentialType.EMAIL_PASSWORD)){
             log.warn("event=REGISTRATION_DUPLICATE_CREDENTIAL email={}", email);
             throw new DuplicateEmailException(email);
         }
         // ─────────────────────────────────────────────────────────────────
         // Build and persist AuthUser.
         // ULID + timestamps set by @PrePersist — not set here.
         // ─────────────────────────────────────────────────────────────────
         AuthUser authUser = new AuthUser();
         authUser.setEmail(email);
         authUser.setFirstName(dto.firstName().strip());
         authUser.setLastName(dto.lastName().strip());

         AuthUser savedUser = authUserRepository.save(authUser);

         // savedUser.getId() is now populated by @PrePersist.

         // ─────────────────────────────────────────────────────────────────
         // Build and persist AuthCredential.
         // Raw password is BCrypt-hashed here and immediately discarded.
         // It never touches the entity constructor, never appears in a log.
         // ─────────────────────────────────────────────────────────────────

         AuthCredential credential = new AuthCredential();
         credential.setAuthUser(savedUser);
         credential.setCredentialType(CredentialType.EMAIL_PASSWORD);
         credential.setEmail(email);
         credential.setPasswordHash(passwordEncoder.encode(dto.password()));

         authCredentialRepository.save(credential);

         // ─────────────────────────────────────────────────────────────────
         // Build typed payload and write outbox event.
         //
         // UserRegisteredPayload is a typed record — ObjectMapper serializes
         // it to JSON inside OutboxEvent.of(). No hand-built strings, no
         // escaping bugs, no schema drift.
         //
         // This INSERT is in the same transaction as the auth_users and
         // auth_credentials inserts above. If the transaction commits,
         // all three rows are durable. The event cannot be lost.
         // ─────────────────────────────────────────────────────────────────

         UserRegisteredPayload payload = new UserRegisteredPayload(
                 savedUser.getId(),
                 email,
                 savedUser.getFirstName(),
                 savedUser.getLastName()
         );

         OutboxEvent outboxEvent = OutboxEvent.of(
                 savedUser.getId(),
                 "AuthUser",
                 "user.registered",
                 TOPIC_USER_REGISTERED,
                 payload
         );

         outboxEventRepository.save(outboxEvent);

         outboxEventRepository.save(outboxEvent);

         log.info(
                 "event=REGISTRATION_SUCCESS userId={} email={} outboxEventId={}",
                 savedUser.getId(), email, outboxEvent.getId()
         );

         return new RegisterResponseDto(
                 savedUser.getId(),
                 email,
                 "Registration successful. Please verify your email."
         );


     }
}
