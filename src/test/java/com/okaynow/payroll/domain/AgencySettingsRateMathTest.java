package com.okaynow.payroll.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AgencySettingsRateMathTest {

    @Test
    void billRateFromPayRateIsInverseOfSuggestedPayRate() {
        AgencySettings settings = AgencySettings.builder()
                .id(1L)
                .agencyTakePercent(new BigDecimal("35.00"))
                .defaultPayRate(new BigDecimal("22.00"))
                .build();

        BigDecimal pay = new BigDecimal("22.00");
        BigDecimal bill = settings.billRateFromPayRate(pay);
        assertThat(bill).isEqualByComparingTo("33.85");
        assertThat(settings.suggestedPayRate(bill)).isEqualByComparingTo("22.00");
    }

    @Test
    void twentyPercentTake() {
        AgencySettings settings = AgencySettings.builder()
                .id(1L)
                .agencyTakePercent(new BigDecimal("20.00"))
                .build();

        assertThat(settings.billRateFromPayRate(new BigDecimal("22.00")))
                .isEqualByComparingTo("27.50");
    }
}
