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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgencySettingsService {

    private final AgencySettingsRepository agencySettingsRepository;

    /**
     * Legacy platform-wide settings (pre-tenant marketplace / admin console).
     */
    @Transactional
    public AgencySettings getOrCreate() {
        return normalize(agencySettingsRepository.findById(AgencySettings.SINGLETON_ID)
                .orElseGet(() -> agencySettingsRepository.save(defaults(AgencySettings.SINGLETON_ID, null))));
    }

    /**
     * Per-tenant economics. Seeds from platform defaults when the agency has no row yet.
     */
    @Transactional
    public AgencySettings getOrCreateForAgency(UUID agencyId) {
        if (agencyId == null) {
            return getOrCreate();
        }
        return normalize(agencySettingsRepository.findByAgencyId(agencyId)
                .orElseGet(() -> {
                    AgencySettings seed = getOrCreate();
                    long nextId = Math.max(agencySettingsRepository.findMaxId() + 1, 2L);
                    AgencySettings created = AgencySettings.builder()
                            .id(nextId)
                            .agencyId(agencyId)
                            .agencyTakePercent(seed.getAgencyTakePercent())
                            .defaultPayRate(seed.getDefaultPayRate())
                            .defaultBillRate(seed.getDefaultBillRate())
                            .payPeriodType(seed.getPayPeriodType())
                            .periodStartDay(seed.getPeriodStartDay())
                            .autoInvoiceOnComplete(seed.isAutoInvoiceOnComplete())
                            .autoInvoiceSendImmediately(seed.isAutoInvoiceSendImmediately())
                            .clientCaregiverRejectionFee(seed.getClientCaregiverRejectionFee())
                            .platformConversionFee(seed.getPlatformConversionFee())
                            .shiftRoutingMode(seed.getShiftRoutingMode())
                            .maxIncompleteShiftsPerCaregiver(seed.getMaxIncompleteShiftsPerCaregiver())
                            .minBufferMinutesBetweenShifts(seed.getMinBufferMinutesBetweenShifts())
                            .maxDriveMinutesBetweenShifts(seed.getMaxDriveMinutesBetweenShifts())
                            .build();
                    return agencySettingsRepository.save(created);
                }));
    }

    @Transactional
    public AgencySettingsResponse getResponse() {
        return toResponse(getOrCreate());
    }

    @Transactional
    public AgencySettingsResponse getResponseForAgency(UUID agencyId) {
        return toResponse(getOrCreateForAgency(agencyId));
    }

    @Transactional
    public ClientRateCardResponse clientRateCard() {
        return toClientRateCard(getOrCreate());
    }

    @Transactional
    public ClientRateCardResponse clientRateCardForAgency(UUID agencyId) {
        return toClientRateCard(getOrCreateForAgency(agencyId));
    }

    @Transactional
    public AgencySettingsResponse update(UpdateAgencySettingsRequest request) {
        return applyUpdate(getOrCreate(), request);
    }

    @Transactional
    public AgencySettingsResponse updateForAgency(UUID agencyId, UpdateAgencySettingsRequest request) {
        return applyUpdate(getOrCreateForAgency(agencyId), request);
    }

    private AgencySettingsResponse applyUpdate(AgencySettings settings, UpdateAgencySettingsRequest request) {
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
        settings.setShiftRoutingMode(request.shiftRoutingMode());
        settings.setMaxIncompleteShiftsPerCaregiver(request.maxIncompleteShiftsPerCaregiver());
        settings.setMinBufferMinutesBetweenShifts(request.minBufferMinutesBetweenShifts());
        settings.setMaxDriveMinutesBetweenShifts(request.maxDriveMinutesBetweenShifts());
        return toResponse(agencySettingsRepository.save(settings));
    }

    private ClientRateCardResponse toClientRateCard(AgencySettings settings) {
        BigDecimal pay = settings.getDefaultPayRate().setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = settings.getClientCaregiverRejectionFee() != null
                ? settings.getClientCaregiverRejectionFee().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal conversion = settings.getPlatformConversionFee() != null
                ? settings.getPlatformConversionFee().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return new ClientRateCardResponse(settings.billRateFromPayRate(pay), fee, conversion);
    }

    private AgencySettings normalize(AgencySettings settings) {
        boolean dirty = false;
        if (settings.getDefaultPayRate() == null) {
            if (settings.getDefaultBillRate() != null) {
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
        if (settings.getShiftRoutingMode() == null) {
            settings.setShiftRoutingMode(com.okaynow.agencies.domain.ShiftRoutingMode.INBOX_FIRST);
            dirty = true;
        }
        if (dirty) {
            settings = agencySettingsRepository.save(settings);
        }
        return settings;
    }

    private static AgencySettings defaults(long id, UUID agencyId) {
        return AgencySettings.builder()
                .id(id)
                .agencyId(agencyId)
                .agencyTakePercent(new BigDecimal("35.00"))
                .defaultPayRate(new BigDecimal("22.00"))
                .payPeriodType(PayPeriodType.WEEKLY)
                .periodStartDay(DayOfWeek.MONDAY)
                .build();
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
                        : BigDecimal.ZERO,
                settings.getShiftRoutingMode(),
                settings.getMaxIncompleteShiftsPerCaregiver(),
                settings.getMinBufferMinutesBetweenShifts(),
                settings.getMaxDriveMinutesBetweenShifts());
    }
}
