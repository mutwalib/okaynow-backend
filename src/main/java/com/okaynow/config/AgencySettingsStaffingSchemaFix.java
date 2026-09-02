package com.okaynow.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(7)
@RequiredArgsConstructor
@Slf4j
public class AgencySettingsStaffingSchemaFix implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    ALTER TABLE agency_settings
                    ADD COLUMN IF NOT EXISTS shift_routing_mode varchar(24) NOT NULL DEFAULT 'INBOX_FIRST'
                    """);
            jdbcTemplate.execute("""
                    ALTER TABLE agency_settings
                    ADD COLUMN IF NOT EXISTS max_incomplete_shifts_per_caregiver integer NOT NULL DEFAULT 3
                    """);
            jdbcTemplate.execute("""
                    ALTER TABLE agency_settings
                    ADD COLUMN IF NOT EXISTS min_buffer_minutes_between_shifts integer NOT NULL DEFAULT 15
                    """);
            jdbcTemplate.execute("""
                    ALTER TABLE agency_settings
                    ADD COLUMN IF NOT EXISTS max_drive_minutes_between_shifts integer NOT NULL DEFAULT 45
                    """);
        } catch (Exception ex) {
            log.warn("agency_settings staffing schema fix skipped: {}", ex.getMessage());
        }
    }
}
