package com.okaynow.payroll.service;

import com.okaynow.common.dto.PagedResponse;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.payroll.domain.PaymentStatus;
import com.okaynow.payroll.domain.ShiftSettlement;
import com.okaynow.payroll.dto.CaregiverAgencyPaySlice;
import com.okaynow.payroll.dto.CaregiverPayEntryResponse;
import com.okaynow.payroll.dto.CaregiverPaySummaryResponse;
import com.okaynow.payroll.repository.ShiftSettlementRepository;
import com.okaynow.payroll.support.PayPeriodCalculator;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.shifts.service.ShiftAgencyLabelService;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaregiverPayrollService {

    private static final String INDEPENDENT_LABEL = "Independent";

    private final ShiftSettlementRepository settlementRepository;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final ShiftRepository shiftRepository;
    private final UserService userService;
    private final SettlementService settlementService;
    private final ShiftAgencyLabelService shiftAgencyLabelService;

    @Transactional
    public CaregiverPaySummaryResponse summary(String caregiverEmail, LocalDate periodStart, LocalDate periodEnd) {
        CaregiverProfile caregiver = profile(caregiverEmail);
        var bounds = resolvePeriod(periodStart, periodEnd);
        settlementService.backfillCompletedInPeriod(bounds.start(), bounds.end());

        List<ShiftSettlement> rows = settlementRepository.findAllCaregiverByShiftDateRange(
                caregiver.getId(), bounds.start(), bounds.end());
        Map<UUID, Shift> shiftsById = loadShifts(rows);
        Map<UUID, String> agencyNames = shiftAgencyLabelService.namesFor(
                shiftsById.values().stream().map(Shift::getAgencyId).filter(Objects::nonNull).toList());

        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        BigDecimal hours = zero;
        BigDecimal earned = zero;
        BigDecimal paid = zero;
        Map<String, Acc> byKey = new LinkedHashMap<>();
        for (ShiftSettlement s : rows) {
            hours = hours.add(s.getHours());
            earned = earned.add(s.getCaregiverAmount());
            boolean isPaid = s.getCaregiverPaymentStatus() == PaymentStatus.PAID;
            if (isPaid) {
                paid = paid.add(s.getCaregiverAmount());
            }
            Shift shift = shiftsById.get(s.getShiftId());
            UUID agencyId = shift != null ? shift.getAgencyId() : null;
            String key = agencyId != null ? agencyId.toString() : "independent";
            String label = agencyId != null
                    ? agencyNames.getOrDefault(agencyId, "Agency")
                    : INDEPENDENT_LABEL;
            Acc acc = byKey.computeIfAbsent(key, k -> new Acc(agencyId, label));
            acc.shiftCount++;
            acc.hours = acc.hours.add(s.getHours());
            acc.earned = acc.earned.add(s.getCaregiverAmount());
            if (isPaid) {
                acc.paid = acc.paid.add(s.getCaregiverAmount());
            }
        }

        List<CaregiverAgencyPaySlice> byAgency = byKey.values().stream()
                .sorted(Comparator
                        .comparing((Acc a) -> a.agencyId == null ? 0 : 1)
                        .thenComparing(a -> a.label, String.CASE_INSENSITIVE_ORDER))
                .map(a -> new CaregiverAgencyPaySlice(
                        a.agencyId,
                        a.label,
                        a.shiftCount,
                        a.hours,
                        a.earned,
                        a.paid,
                        a.earned.subtract(a.paid)))
                .toList();

        return new CaregiverPaySummaryResponse(
                bounds.start(),
                bounds.end(),
                rows.size(),
                hours,
                earned,
                paid,
                earned.subtract(paid),
                byAgency);
    }

    @Transactional
    public PagedResponse<CaregiverPayEntryResponse> entries(
            String caregiverEmail, LocalDate periodStart, LocalDate periodEnd, Pageable pageable) {
        CaregiverProfile caregiver = profile(caregiverEmail);
        var bounds = resolvePeriod(periodStart, periodEnd);
        settlementService.backfillCompletedInPeriod(bounds.start(), bounds.end());

        var page = settlementRepository.findCaregiverByShiftDateRange(
                caregiver.getId(), bounds.start(), bounds.end(), pageable);
        Map<UUID, Shift> shiftsById = loadShifts(page.getContent());
        Map<UUID, String> agencyNames = shiftAgencyLabelService.namesFor(
                shiftsById.values().stream().map(Shift::getAgencyId).filter(Objects::nonNull).toList());

        return PagedResponse.from(page.map(s -> toEntry(s, shiftsById, agencyNames)));
    }

    private CaregiverPayEntryResponse toEntry(
            ShiftSettlement s,
            Map<UUID, Shift> shiftsById,
            Map<UUID, String> agencyNames) {
        Shift shift = shiftsById.get(s.getShiftId());
        if (shift == null) {
            shift = shiftRepository.findById(s.getShiftId()).orElse(null);
        }
        LocalTime startTime = shift != null ? shift.getStartTime() : null;
        LocalTime endTime = shift != null ? shift.getEndTime() : null;
        boolean endsNextDay = startTime != null && endTime != null && !endTime.isAfter(startTime);

        String clientFirst = null;
        String clientLast = null;
        UUID clientId = s.getClientProfileId() != null
                ? s.getClientProfileId()
                : (shift != null ? shift.getClientProfileId() : null);
        if (clientId != null) {
            ClientProfile client = clientProfileRepository.findById(clientId).orElse(null);
            if (client != null) {
                clientFirst = client.getFirstName();
                clientLast = client.getLastName();
            }
        }

        UUID agencyId = shift != null ? shift.getAgencyId() : null;
        String agencyName = agencyId != null
                ? agencyNames.getOrDefault(agencyId, "Agency")
                : null;

        return new CaregiverPayEntryResponse(
                s.getId(),
                s.getShiftId(),
                s.getShiftDate(),
                startTime,
                endTime,
                endsNextDay,
                clientFirst,
                clientLast,
                s.getHours(),
                s.getPayRate(),
                s.getCaregiverAmount(),
                s.getCaregiverPaymentStatus(),
                s.getPayPeriodStart(),
                s.getPayPeriodEnd(),
                s.getCaregiverPaidAt(),
                agencyId,
                agencyName);
    }

    private Map<UUID, Shift> loadShifts(List<ShiftSettlement> rows) {
        List<UUID> ids = rows.stream().map(ShiftSettlement::getShiftId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return shiftRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Shift::getId, s -> s, (a, b) -> a, HashMap::new));
    }

    private CaregiverProfile profile(String email) {
        User user = userService.getByEmail(email);
        return caregiverProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
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

    private static final class Acc {
        final UUID agencyId;
        final String label;
        long shiftCount;
        BigDecimal hours = BigDecimal.ZERO.setScale(2);
        BigDecimal earned = BigDecimal.ZERO.setScale(2);
        BigDecimal paid = BigDecimal.ZERO.setScale(2);

        Acc(UUID agencyId, String label) {
            this.agencyId = agencyId;
            this.label = label;
        }
    }
}
