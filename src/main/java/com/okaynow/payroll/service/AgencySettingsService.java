package com.okaynow.payroll.service;

import com.okaynow.payroll.domain.AgencySettings;
import com.okaynow.payroll.domain.PayPeriodType;
import com.okaynow.payroll.dto.AgencySettingsResponse;
import com.okaynow.payroll.dto.ClientRateCardResponse;
import com.okaynow.payroll.dto.UpdateAgencySettingsRequest;
import com.okaynow.payroll.repository.AgencySettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;

@Service
@RequiredArgsConstructor
public class AgencySettingsService {

    private final AgencySettingsRepository agencySettingsRepository;

    @Transactional
    public AgencySettings getOrCreate() {
        AgencySettings settings = agencySettingsRepository.findById(AgencySettings.SINGLETON_ID)
                .orElseGet(() -> agencySettingsRepository.save(AgencySettings.builder()
                        .id(AgencySettings.SINGLETON_ID)
                        .agencyTakePercent(new BigDecimal("35.00"))
                        .defaultPayRate(new BigDecimal("22.00"))
                        .payPeriodType(PayPeriodType.WEEKLY)
                        .periodStartDay(DayOfWeek.MONDAY)
                        .build()));
        boolean dirty = false;
        if (settings.getDefaultPayRate() == null) {
            if (settings.getDefaultBillRate() != null) {
                // Old installs stored a fixed bill; convert to caregiver pay at current take %.
                settings.setDefaultPayRate(settings.suggestedPayRate(settings.getDefaultBillRate()));
            } else {
                settings.setDefaultPayRate(new BigDecimal("22.00"));
            }
            dirty = true;
        }
        if (settings.getClientCaregiverRejectionFee() == null) {
            settings.setClientCaregiverRejectionFee(new BigDecimal("25.00"));
            dirty = true;
        }
        if (settings.getPlatformConversionFee() == null) {
            settings.setPlatformConversionFee(new BigDecimal("500.00"));
            dirty = true;
        }
        if (dirty) {
            settings = agencySettingsRepository.save(settings);
        }
        return settings;
    }

    @Transactional
    public AgencySettingsResponse getResponse() {
        return toResponse(getOrCreate());
    }

    @Transactional
    public ClientRateCardResponse clientRateCard() {
        AgencySettings settings = getOrCreate();
        BigDecimal pay = settings.getDefaultPayRate().setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = settings.getClientCaregiverRejectionFee() != null
                ? settings.getClientCaregiverRejectionFee().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal conversion = settings.getPlatformConversionFee() != null
                ? settings.getPlatformConversionFee().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return new ClientRateCardResponse(settings.billRateFromPayRate(pay), fee, conversion);
    }

    @Transactional
    public AgencySettingsResponse update(UpdateAgencySettingsRequest request) {
        AgencySettings settings = getOrCreate();
        settings.setAgencyTakePercent(request.agencyTakePercent().setScale(2, RoundingMode.HALF_UP));
        settings.setDefaultPayRate(request.defaultPayRate().setScale(2, RoundingMode.HALF_UP));
        settings.setPayPeriodType(request.payPeriodType());
        settings.setPeriodStartDay(request.periodStartDay());
        settings.setAutoInvoiceOnComplete(Boolean.TRUE.equals(request.autoInvoiceOnComplete()));
        settings.setAutoInvoiceSendImmediately(Boolean.TRUE.equals(request.autoInvoiceSendImmediately()));
        settings.setClientCaregiverRejectionFee(
                request.clientCaregiverRejectionFee().setScale(2, RoundingMode.HALF_UP));
        settings.setPlatformConversionFee(
                request.platformConversionFee().setScale(2, RoundingMode.HALF_UP));
        return toResponse(agencySettingsRepository.save(settings));
    }

    private AgencySettingsResponse toResponse(AgencySettings settings) {
        return new AgencySettingsResponse(
                settings.getAgencyTakePercent(),
                settings.getDefaultPayRate(),
                settings.getPayPeriodType(),
                settings.getPeriodStartDay(),
                settings.isAutoInvoiceOnComplete(),
                settings.isAutoInvoiceSendImmediately(),
                settings.getClientCaregiverRejectionFee() != null
                        ? settings.getClientCaregiverRejectionFee()
                        : BigDecimal.ZERO,
                settings.getPlatformConversionFee() != null
                        ? settings.getPlatformConversionFee()
                        : BigDecimal.ZERO);
    }
}
