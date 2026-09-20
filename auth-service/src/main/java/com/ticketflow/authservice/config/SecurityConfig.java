package com.ticketflow.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SecurityConfig — Spring Security 6.x configuration for auth-service.
 *
 * Declares:
 *   SecurityFilterChain — which endpoints are public vs protected
 *   PasswordEncoder     — BCrypt cost 12, used by AuthService
 *   AuthenticationManager — needed for login feature (Feature 2)
 *
 * This is the ONLY class in auth-service that configures security.
 * No security logic lives in controllers or services.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * SecurityFilterChain — the core security configuration.
     *
     * Every HTTP request passes through this filter chain before
     * reaching any controller. Order of configuration matters.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ─────────────────────────────────────────────────────────────
                // CSRF: disabled.
                // Safe for stateless JWT APIs — see design decisions above.
                // Enabling CSRF on a stateless API adds no security benefit
                // and breaks all non-browser API clients (mobile, Postman).
                // ─────────────────────────────────────────────────────────────
                .csrf(AbstractHttpConfigurer::disable)

                // ─────────────────────────────────────────────────────────────
                // SESSION: stateless.
                // auth-service never creates or uses HttpSession.
                // Security context is rebuilt on every request from the JWT.
                // Without STATELESS, Spring Security may silently create
                // sessions, breaking the distributed stateless design.
                // ─────────────────────────────────────────────────────────────
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ─────────────────────────────────────────────────────────────
                // AUTHORIZATION RULES
                //
                // Public endpoints — no JWT required:
                //   All auth endpoints (/api/v1/auth/**) are public by design.
                //   auth-service IS the token issuer. It cannot require a token
                //   to issue a token — that is a circular dependency.
                //
                // Actuator health — public for K8s liveness/readiness probes.
                //   K8s probes do not send auth headers. Health must be public.
                //
                // Everything else — requires authentication.
                //   auth-service has no other public surface beyond auth endpoints.
                //   Any accidental route exposure is blocked by default.
                // ─────────────────────────────────────────────────────────────
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .anyRequest().authenticated()
                )

                // ─────────────────────────────────────────────────────────────
                // FORM LOGIN: disabled.
                // Form login is for browser-based session authentication.
                // We use JWT. Disabling removes the default /login page and
                // the UsernamePasswordAuthenticationFilter from the chain.
                // ─────────────────────────────────────────────────────────────
                .formLogin(AbstractHttpConfigurer::disable)

                // ─────────────────────────────────────────────────────────────
                // HTTP BASIC: disabled.
                // HTTP Basic sends credentials on every request in a header.
                // We use JWT. Disabling removes BasicAuthenticationFilter
                // from the chain and prevents the browser popup on 401.
                // ─────────────────────────────────────────────────────────────
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /**
     * PasswordEncoder — BCrypt with cost factor 12.
     *
     * Cost factor 12 = 2^12 = 4096 hashing rounds.
     * ~250ms per hash on modern hardware. Intentionally slow.
     * Makes brute-force attacks computationally infeasible.
     *
     * Never reduce this for performance. Registration is low-frequency.
     * If login latency is a concern (cost 12 at high login RPS), the
     * correct fix is horizontal scaling, not reducing BCrypt cost.
     *
     * Declared here (not in AuthService) because PasswordEncoder is a
     * Spring Security concern. AuthService receives it via injection.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * AuthenticationManager — exposed as a bean for Feature 2 (Login).
     *
     * Spring Security's AuthenticationManager orchestrates the
     * authentication process: load user → verify credentials → return
     * authenticated principal. We need it as a bean in AuthService
     * for the login flow where we call authenticate(usernamePasswordToken).
     *
     * Declared now so the bean exists when we add login in Feature 2.
     * Nothing calls it in Feature 1.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}