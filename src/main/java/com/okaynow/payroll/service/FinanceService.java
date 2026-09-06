package com.okaynow.payroll.service;

import com.okaynow.common.dto.PagedResponse;
import com.okaynow.payroll.domain.PaymentStatus;
import com.okaynow.payroll.domain.ShiftSettlement;
import com.okaynow.payroll.dto.FinanceSummaryResponse;
import com.okaynow.payroll.dto.SettlementResponse;
import com.okaynow.payroll.repository.ShiftSettlementRepository;
import com.okaynow.payroll.support.PayPeriodCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinanceService {

    private final ShiftSettlementRepository settlementRepository;
    private final SettlementService settlementService;

    @Transactional
    public FinanceSummaryResponse summary(LocalDate periodStart, LocalDate periodEnd) {
        var bounds = resolvePeriod(periodStart, periodEnd);
        settlementService.backfillCompletedInPeriod(bounds.start(), bounds.end());
        return summarize(settlementRepository.findAllByShiftDateRange(bounds.start(), bounds.end()), bounds);
    }

    @Transactional
    public FinanceSummaryResponse summaryForAgency(
            UUID agencyId, LocalDate periodStart, LocalDate periodEnd) {
        var bounds = resolvePeriod(periodStart, periodEnd);
        settlementService.backfillCompletedInPeriod(bounds.start(), bounds.end());
        return summarize(
                settlementRepository.findAllByAgencyAndShiftDateRange(
                        agencyId, bounds.start(), bounds.end()),
                bounds);
    }

    @Transactional
    public PagedResponse<SettlementResponse> listSettlements(
            LocalDate periodStart,
            LocalDate periodEnd,
            PaymentStatus clientStatus,
            PaymentStatus caregiverStatus,
            String q,
            Pageable pageable) {
        var bounds = resolvePeriod(periodStart, periodEnd);
        settlementService.backfillCompletedInPeriod(bounds.start(), bounds.end());

        String query = normalizeQuery(q);
        return PagedResponse.from(
                settlementRepository.searchByShiftDateRange(
                                bounds.start(), bounds.end(), clientStatus, caregiverStatus, query, pageable)
                        .map(settlementService::toAdminResponse));
    }

    @Transactional
    public PagedResponse<SettlementResponse> listSettlementsForAgency(
            UUID agencyId,
            LocalDate periodStart,
            LocalDate periodEnd,
            PaymentStatus clientStatus,
            PaymentStatus caregiverStatus,
            String q,
            Pageable pageable) {
        var bounds = resolvePeriod(periodStart, periodEnd);
        settlementService.backfillCompletedInPeriod(bounds.start(), bounds.end());
        String query = normalizeQuery(q);
        return PagedResponse.from(
                settlementRepository.searchByAgencyAndShiftDateRange(
                                agencyId,
                                bounds.start(),
                                bounds.end(),
                                clientStatus,
                                caregiverStatus,
                                query,
                                pageable)
                        .map(settlementService::toAdminResponse));
    }

    private static FinanceSummaryResponse summarize(
            List<ShiftSettlement> rows, PayPeriodCalculator.Bounds bounds) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        BigDecimal totalHours = zero;
        BigDecimal clientBilled = zero;
        BigDecimal clientCollected = zero;
        BigDecimal caregiverOwed = zero;
        BigDecimal caregiverPaid = zero;
        BigDecimal agencyMargin = zero;
        BigDecimal agencyCollected = zero;

        for (ShiftSettlement s : rows) {
            totalHours = totalHours.add(s.getHours());
            clientBilled = clientBilled.add(s.getClientAmount());
            caregiverOwed = caregiverOwed.add(s.getCaregiverAmount());
            agencyMargin = agencyMargin.add(s.getAgencyAmount());
            if (s.getClientPaymentStatus() == PaymentStatus.PAID) {
                clientCollected = clientCollected.add(s.getClientAmount());
                agencyCollected = agencyCollected.add(s.getAgencyAmount());
            }
            if (s.getCaregiverPaymentStatus() == PaymentStatus.PAID) {
                caregiverPaid = caregiverPaid.add(s.getCaregiverAmount());
            }
        }

        return new FinanceSummaryResponse(
                bounds.start(),
                bounds.end(),
                rows.size(),
                totalHours,
                clientBilled,
                clientCollected,
                clientBilled.subtract(clientCollected),
                caregiverOwed,
                caregiverPaid,
                caregiverOwed.subtract(caregiverPaid),
                agencyMargin,
                agencyCollected);
    }

    private static String normalizeQuery(String q) {
        String query = q == null ? null : q.trim();
        if (query != null && query.isEmpty()) {
            return null;
        }
        return query;
    }

    private PayPeriodCalculator.Bounds resolvePeriod(LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart != null && periodEnd != null) {
            return new PayPeriodCalculator.Bounds(periodStart, periodEnd);
        }
        if (periodStart != null) {
            return new PayPeriodCalculator.Bounds(periodStart, LocalDate.now());
        }
        return PayPeriodCalculator.lastSevenDaysThroughToday();
    }
}
