package com.okaynow.payroll.dto;

import java.math.BigDecimal;

/**
 * Client-facing rate / policy card.
 */
public record ClientRateCardResponse(
        BigDecimal billRate,
        BigDecimal caregiverRejectionFee,
        BigDecimal platformConversionFee
) {
}
