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
 * Facility invoices and fee-only lines (rejection / platform conversion) need nullable
 * client_profile_id, shift_id, and settlement_id.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class ClientInvoiceNullableClientBackfill implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        dropNotNull("client_invoices", "client_profile_id");
        dropNotNull("client_invoice_lines", "shift_id");
        dropNotNull("client_invoice_lines", "settlement_id");
    }

    private void dropNotNull(String table, String column) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE " + table + " ALTER COLUMN " + column + " DROP NOT NULL");
            log.info("Ensured {}.{} is nullable (fee / facility invoices)", table, column);
        } catch (Exception ex) {
            log.debug("{}.{} nullable sync skipped: {}", table, column, ex.getMessage());
        }
    }
}
