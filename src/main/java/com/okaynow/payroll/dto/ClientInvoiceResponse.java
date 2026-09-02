package com.okaynow.payroll.dto;

import com.okaynow.payroll.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ClientInvoiceResponse(
        UUID id,
        String invoiceNumber,
        UUID agencyId,
        UUID clientProfileId,
        String clientFirstName,
        String clientLastName,
        UUID facilityProfileId,
        String facilityName,
        InvoiceStatus status,
        LocalDate issuedDate,
        LocalDate dueDate,
        BigDecimal totalAmount,
        String notes,
        Instant sentAt,
        Instant paidAt,
        Instant voidedAt,
        Instant createdAt,
        boolean payableOnline,
        List<ClientInvoiceLineResponse> lines
) {
}
