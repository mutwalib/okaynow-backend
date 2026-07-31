package com.okaynow.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ClientInvoiceLineResponse(
        UUID id,
        UUID settlementId,
        UUID shiftId,
        LocalDate shiftDate,
        String description,
        BigDecimal hours,
        BigDecimal billRate,
        BigDecimal amount
) {
}
