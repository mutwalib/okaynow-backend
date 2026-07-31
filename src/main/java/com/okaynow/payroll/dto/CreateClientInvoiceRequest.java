package com.okaynow.payroll.dto;

import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateClientInvoiceRequest(
        /** Family client bill-to — set exactly one of clientProfileId / facilityProfileId. */
        UUID clientProfileId,
        /** Facility bill-to — set exactly one of clientProfileId / facilityProfileId. */
        UUID facilityProfileId,
        @NotEmpty List<UUID> settlementIds,
        LocalDate dueDate,
        String notes,
        /** When true, invoice is created as SENT and the bill-to party is notified. */
        boolean sendNow
) {
}
