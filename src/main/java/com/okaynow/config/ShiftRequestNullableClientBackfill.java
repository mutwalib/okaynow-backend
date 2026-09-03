package com.okaynow.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate ddl-auto=update does not drop NOT NULL when a column becomes optional.
 * Facility-posted shift requests have no home client profile.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class ShiftRequestNullableClientBackfill implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE shift_requests ALTER COLUMN client_profile_id DROP NOT NULL");
            log.info("Ensured shift_requests.client_profile_id is nullable (facility openings)");
        } catch (Exception ex) {
            log.debug("shift_requests.client_profile_id nullable sync skipped: {}", ex.getMessage());
        }
    }
}
