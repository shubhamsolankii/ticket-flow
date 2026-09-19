package com.ticketflow.authservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
/**
 * Listens for UserRegisteredEvent AFTER the DB transaction commits,
 * then publishes the user.registered Kafka message.
 *
 * Why @TransactionalEventListener(phase = AFTER_COMMIT)?
 * If we published Kafka inside the @Transactional block in AuthService
 * and the Kafka send failed, Spring would roll back the DB writes —
 * the user's account would be deleted even though we already returned
 * HTTP 201 to the client. AFTER_COMMIT guarantees the DB write is
 * durable before we attempt the Kafka publish.
 *
 * Failure mode: DB committed, Kafka publish fails.
 * The user account exists. The Kafka event was not delivered.
 * user-service will not create the profile immediately.
 * Mitigation: KafkaTemplate retries (configured in application.yml:
 * producer.retries=3). If all retries fail, the event is lost.
 * Production fix: outbox pattern (future enhancement — not in scope now).
 */
@Component
public class UserRegisteredEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(UserRegisteredEventPublisher.class);
    private static final String TOPIC = "user.registered";
    private final KafkaTemplate<String, String> kafkaTemplate;

    public UserRegisteredEventPublisher(KafkaTemplate<String, String> kafkaTemplate){
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(UserRegisteredEvent event){
        // Kafka message key = userId (ULID).
        // Key-based partitioning: all events for the same user go to the
        // same partition, preserving order for that user's event stream.
        String messageKey = event.userId();

        // Simple JSON payload. In a later iteration, replace with a proper
        // serializer (Jackson ObjectMapper or Avro schema). For Feature 1,
        // a hand-built JSON string is sufficient and has zero extra dependencies.
        String payload = String.format(
                "{\"userId\":\"%s\",\"email\":\"%s\",\"firstName\":\"%s\",\"lastName\":\"%s\"}",
                event.userId(),
                event.email(),
                event.firstName(),
                event.lastName()
        );

        kafkaTemplate.send(TOPIC, messageKey, payload)
                .whenComplete((result, ex) ->{
                    if(ex != null){
                        // Log the failure. The user account exists in DB.
                        // user-service profile creation will be delayed or missed.
                        // Production resolution: outbox pattern or a reconciliation job.

                        log.error(
                                "event=USER_REGISTERED_KAFKA_PUBLISH_FAILED userId={} email={} error={}",
                                event.userId(), event.email(), ex.getMessage()
                        );
                    }
                    else{
                        log.info(
                                "event=USER_REGISTERED_KAFKA_PUBLISHED userId={} topic={} partition={} offset={}",
                                event.userId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset()
                        );
                    }
        })

    }
}
