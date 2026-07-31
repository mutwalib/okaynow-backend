package com.okaynow.booking.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Result of a client rejecting a claimed/assigned caregiver. */
public record ClientRejectCaregiverResponse(
        ShiftClaimResponse claim,
        BigDecimal feeCharged,
        UUID feeInvoiceId,
        String feeInvoiceNumber
) {
}
