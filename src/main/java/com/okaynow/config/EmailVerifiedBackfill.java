package com.okaynow.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Existing ACTIVE accounts predate email verification — treat them as verified.
 */
@Component
@Order(20)
@RequiredArgsConstructor
@Slf4j
public class EmailVerifiedBackfill implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
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
            log.debug("email_verified backfill skipped: {}", ex.getMessage());
        }
    }
}
