package com.ticketflow.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AuthServiceApplication — entry point for the auth-service.
 *
 * @SpringBootApplication is a composite of:
 *   @Configuration      — this class can declare @Bean methods
 *   @EnableAutoConfiguration — Spring Boot wires up everything on the classpath
 *   @ComponentScan      — scans com.ticketflow.authservice and all sub-packages
 *
 * @EnableScheduling — activates Spring's scheduled task executor.
 * Without this, OutboxEventPublisher's @Scheduled(fixedDelay=1000)
 * is silently ignored. The outbox poller will never run.
 * This is non-negotiable for the outbox pattern to function.
 */
@SpringBootApplication
@EnableScheduling
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}