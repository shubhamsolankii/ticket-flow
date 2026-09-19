package com.ticketflow.authservice.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;


/**
 * OutboxEventPublisher — the outbox poller.
 *
 * Runs every 1 second on a scheduled thread.
 * Reads PENDING rows from outbox_events, publishes to Kafka,
 * marks as PUBLISHED on success or increments retry_count on failure.
 *
 * Each poll runs in its own @Transactional boundary — separate from
 * the registration transaction that wrote the outbox row.
 *
 * High-scale notes:
 *   - LIMIT 100 per poll: prevents a single pod from grabbing all events
 *     and becoming a bottleneck. 100 events/sec/pod × 10 pods = 1000/sec.
 *   - FOR UPDATE SKIP LOCKED: multi-pod safe (see OutboxEventRepository).
 *   - MAX_RETRY_COUNT = 5: after 5 failures, mark FAILED and alert ops.
 *     Prevents a bad event from blocking the queue forever.
 *   - @Scheduled fixedDelay vs fixedRate: fixedDelay waits 1s AFTER the
 *     previous execution completes. fixedRate fires every 1s regardless.
 *     fixedDelay is correct here — if Kafka is slow and a poll takes 3s,
 *     we don't want overlapping polls on the same pod.
 */
@Component
public class OutboxEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY_COUNT = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxEventPublisher(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate){
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void pollAndPublish(){
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEventsWithLock(PageRequest.of(0, BATCH_SIZE));

        if(pendingEvents.isEmpty()){
            return;
        }
        log.debug("event=OUTBOX_POLL count={}", pendingEvents.size());

        for(OutboxEvent event : pendingEvents) {
            publishSingle(event);
        }

    }

    private void publishSingle(OutboxEvent event){
        try{
            // Kafka message key = aggregateId (userId ULID).
            // Key-based partitioning: all events for the same user land on
            // the same partition, preserving event order per user.
            //
            // .get() blocks until Kafka broker acknowledges.
            // Non-blocking (.whenComplete) would return before confirmation —
            // we'd mark PUBLISHED before knowing the broker received it,
            // defeating the outbox durability guarantee entirely.
            kafkaTemplate.send(event.getKafkaTopic(), event.getAggregateId(), event.getPayload()).get();

            event.setStatus("PUBLISHED");
            event.setPublishedAt(Instant.now());
            event.setLastError(null);
        }
        catch(Exception ex){
            int newRetryCount = event.getRetryCount() + 1;
            event.setRetryCount(newRetryCount);
            event.setLastError(ex.getMessage());

            if (newRetryCount >= MAX_RETRY_COUNT) {
                event.setStatus("FAILED");
                log.error(
                        "event=OUTBOX_FAILED id={} type={} aggregateId={} retries={} error={}",
                        event.getId(), event.getEventType(),
                        event.getAggregateId(), newRetryCount, ex.getMessage()
                );
            } else {
                log.warn(
                        "event=OUTBOX_RETRY id={} type={} retryCount={} error={}",
                        event.getId(), event.getEventType(), newRetryCount, ex.getMessage()
                );
            }
        }
    }
}
