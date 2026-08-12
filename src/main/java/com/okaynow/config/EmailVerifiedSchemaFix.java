package com.okaynow.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate {@code ddl-auto=update} cannot add {@code email_verified boolean not null}
 * when rows already exist (no default). Ensure the column before admin bootstrap / JPA use.
 */
@Component
@Order(5)
@RequiredArgsConstructor
@Slf4j
public class EmailVerifiedSchemaFix implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    ALTER TABLE users
                    ADD COLUMN IF NOT EXISTS email_verified boolean NOT NULL DEFAULT FALSE
                    """);
            int n = jdbcTemplate.update("""
                    UPDATE users
                    SET email_verified = TRUE,
                        email_verified_at = COALESCE(email_verified_at, created_at)
                    WHERE status = 'ACTIVE'
                      AND email_verified = FALSE
                    """);
            if (n > 0) {
                log.info("Marked {} existing ACTIVE users as email-verified", n);
            }
        } catch (Exception ex) {
            log.warn("email_verified schema fix skipped: {}", ex.getMessage());
        }
    }
}
