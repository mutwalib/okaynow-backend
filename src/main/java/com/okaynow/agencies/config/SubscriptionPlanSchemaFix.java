package com.okaynow.agencies.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate {@code ddl-auto=update} does not reliably add {@code monthly_price_cents not null}
 * when {@code subscription_plan_definitions} already has rows. Ensure the column exists before
 * plan catalog bootstrap runs.
 */
@Component
@Order(6)
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPlanSchemaFix implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    ALTER TABLE subscription_plan_definitions
                    ADD COLUMN IF NOT EXISTS monthly_price_cents integer NOT NULL DEFAULT 0
                    """);
            jdbcTemplate.update("""
                    UPDATE subscription_plan_definitions
                    SET monthly_price_cents = 29900
                    WHERE plan = 'STARTER' AND monthly_price_cents = 0
                    """);
            jdbcTemplate.update("""
                    UPDATE subscription_plan_definitions
                    SET monthly_price_cents = 79900
                    WHERE plan = 'PROFESSIONAL' AND monthly_price_cents = 0
                    """);
            jdbcTemplate.update("""
                    UPDATE subscription_plan_definitions
                    SET monthly_price_cents = 99900
                    WHERE plan = 'FEATURED' AND monthly_price_cents = 0
                    """);
        } catch (Exception ex) {
            log.warn("subscription plan schema fix skipped: {}", ex.getMessage());
        }
    }
}
