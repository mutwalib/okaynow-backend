package com.okaynow.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures caregiver participation flags exist with safe defaults for existing rows.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class CaregiverParticipationFlagsBackfill implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        addBooleanDefault("independent_shifts_enabled");
        addBooleanDefault("agency_roster_enabled");
    }

    private void addBooleanDefault(String column) {
        try {
            jdbcTemplate.execute("""
                    ALTER TABLE caregiver_profiles
                    ADD COLUMN IF NOT EXISTS %s boolean NOT NULL DEFAULT true
                    """.formatted(column));
            log.info("Ensured caregiver_profiles.{} exists (default true)", column);
        } catch (Exception ex) {
            log.debug("caregiver_profiles.{} sync skipped: {}", column, ex.getMessage());
        }
    }
}
