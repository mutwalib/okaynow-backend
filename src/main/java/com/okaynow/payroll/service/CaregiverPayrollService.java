package com.okaynow.payroll.service;

import com.okaynow.common.dto.PagedResponse;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.payroll.domain.PaymentStatus;
import com.okaynow.payroll.domain.ShiftSettlement;
import com.okaynow.payroll.dto.CaregiverPayEntryResponse;
import com.okaynow.payroll.dto.CaregiverPaySummaryResponse;
import com.okaynow.payroll.repository.ShiftSettlementRepository;
import com.okaynow.payroll.support.PayPeriodCalculator;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.repository.ShiftRepository;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaregiverPayrollService {

    private final ShiftSettlementRepository settlementRepository;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final ShiftRepository shiftRepository;
    private final UserService userService;
    private final SettlementService settlementService;

    @Transactional
    public CaregiverPaySummaryResponse summary(String caregiverEmail, LocalDate periodStart, LocalDate periodEnd) {
        CaregiverProfile caregiver = profile(caregiverEmail);
        var bounds = resolvePeriod(periodStart, periodEnd);
        settlementService.backfillCompletedInPeriod(bounds.start(), bounds.end());

        List<ShiftSettlement> rows = settlementRepository.findAllCaregiverByShiftDateRange(
                caregiver.getId(), bounds.start(), bounds.end());

        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        BigDecimal hours = zero;
        BigDecimal earned = zero;
        BigDecimal paid = zero;
        for (ShiftSettlement s : rows) {
            hours = hours.add(s.getHours());
            earned = earned.add(s.getCaregiverAmount());
            if (s.getCaregiverPaymentStatus() == PaymentStatus.PAID) {
                paid = paid.add(s.getCaregiverAmount());
            }
        }
        return new CaregiverPaySummaryResponse(
                bounds.start(),
                bounds.end(),
                rows.size(),
                hours,
                earned,
                paid,
                earned.subtract(paid));
    }

    @Transactional
    public PagedResponse<CaregiverPayEntryResponse> entries(
            String caregiverEmail, LocalDate periodStart, LocalDate periodEnd, Pageable pageable) {
        CaregiverProfile caregiver = profile(caregiverEmail);
        var bounds = resolvePeriod(periodStart, periodEnd);
        settlementService.backfillCompletedInPeriod(bounds.start(), bounds.end());

        return PagedResponse.from(
                settlementRepository.findCaregiverByShiftDateRange(
                                caregiver.getId(), bounds.start(), bounds.end(), pageable)
                        .map(this::toEntry));
    }

    private CaregiverPayEntryResponse toEntry(ShiftSettlement s) {
        Shift shift = shiftRepository.findById(s.getShiftId()).orElse(null);
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
                s.getCaregiverPaidAt());
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
}
