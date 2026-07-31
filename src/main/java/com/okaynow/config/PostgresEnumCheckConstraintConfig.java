package com.okaynow.config;

import com.okaynow.audit.domain.AuditAction;
import com.okaynow.shifts.domain.ShiftScheduleType;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.evv.domain.ClockMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Hibernate {@code ddl-auto=update} does not widen Postgres enum CHECK constraints
 * when new enum values are added. Rebuild known constraints on startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostgresEnumCheckConstraintConfig implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        sync("audit_logs", "action", "audit_logs_action_check", AuditAction.class);
        sync("shifts", "status", "shifts_status_check", ShiftStatus.class);
        sync("shifts", "schedule_type", "shifts_schedule_type_check", ShiftScheduleType.class);
        sync("visits", "method", "visits_method_check", ClockMethod.class);
        sync("notifications", "type", "notifications_type_check",
                com.okaynow.notifications.domain.NotificationType.class);
        sync("client_invoices", "status", "client_invoices_status_check",
                com.okaynow.payroll.domain.InvoiceStatus.class);
    }

    private void sync(String table, String column, String constraint, Class<? extends Enum<?>> enumType) {
        String allowed = Arrays.stream(enumType.getEnumConstants())
                .map(Enum::name)
                .map(name -> "'" + name + "'")
                .collect(Collectors.joining(", "));
        try {
            jdbcTemplate.execute("ALTER TABLE %s DROP CONSTRAINT IF EXISTS %s"
                    .formatted(table, constraint));
            jdbcTemplate.execute("""
                    ALTER TABLE %s
                    ADD CONSTRAINT %s
                    CHECK (%s IN (%s))
                    """.formatted(table, constraint, column, allowed));
            log.info("Synced {} with {} {} values",
                    constraint, enumType.getEnumConstants().length, enumType.getSimpleName());
        } catch (Exception ex) {
            log.debug("Skipped {} sync: {}", constraint, ex.getMessage());
        }
    }
}
