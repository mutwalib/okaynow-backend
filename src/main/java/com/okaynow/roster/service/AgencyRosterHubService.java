package com.okaynow.roster.service;

import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.booking.domain.ShiftClaim;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.booking.repository.ShiftClaimRepository;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.payroll.domain.PaymentStatus;
import com.okaynow.payroll.domain.ShiftSettlement;
import com.okaynow.payroll.dto.SettlementResponse;
import com.okaynow.payroll.dto.UpdatePaymentStatusRequest;
import com.okaynow.payroll.repository.ShiftSettlementRepository;
import com.okaynow.payroll.service.SettlementService;
import com.okaynow.roster.domain.AgencyCaregiver;
import com.okaynow.roster.dto.AgencyRosterHubResponse;
import com.okaynow.roster.dto.AgencyRosterHubSummary;
import com.okaynow.roster.dto.AgencyRosterMemberDetailResponse;
import com.okaynow.roster.dto.AgencyRosterScheduleItem;
import com.okaynow.roster.dto.AgencyRosterTimesheetItem;
import com.okaynow.roster.repository.AgencyCaregiverRepository;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.users.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgencyRosterHubService {

    private static final EnumSet<ShiftStatus> TERMINAL_SHIFT = EnumSet.of(
            ShiftStatus.COMPLETED,
            ShiftStatus.CANCELLED,
            ShiftStatus.NO_SHOW,
            ShiftStatus.EXPIRED);

    private final AgencyAccessService agencyAccessService;
    private final AgencyCaregiverRepository agencyCaregiverRepository;
    private final AgencyRosterService agencyRosterService;
    private final ShiftClaimRepository shiftClaimRepository;
    private final ShiftSettlementRepository settlementRepository;
    private final ShiftRepository shiftRepository;
    private final SettlementService settlementService;

    @Transactional(readOnly = true)
    public AgencyRosterHubResponse hub(UUID agencyUserId, UUID rosterId, LocalDate from, LocalDate to) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        AgencyCaregiver row = requireAgencyRosterRow(agency, rosterId);
        LocalDate rangeFrom = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate rangeTo = to != null ? to : LocalDate.now().plusDays(30);
        if (rangeTo.isBefore(rangeFrom)) {
            throw new BadRequestException("Provide a valid from/to date range");
        }

        AgencyRosterMemberDetailResponse member = agencyRosterService.getMemberDetail(agencyUserId, rosterId);
        UUID caregiverProfileId = row.getCaregiverProfile().getId();

        List<AgencyRosterScheduleItem> schedule = shiftClaimRepository
                .findAgencyCaregiverSchedule(caregiverProfileId, agency.getId(), rangeFrom, rangeTo)
                .stream()
                .map(this::toScheduleItem)
                .toList();

        List<AgencyRosterTimesheetItem> timesheets = settlementRepository
                .findAgencyCaregiverTimesheets(agency.getId(), caregiverProfileId, rangeFrom, rangeTo)
                .stream()
                .map(this::toTimesheetItem)
                .toList();

        return new AgencyRosterHubResponse(
                member,
                rangeFrom,
                rangeTo,
                summarize(schedule, timesheets),
                schedule,
                timesheets);
    }

    @Transactional
    public SettlementResponse markCaregiverPayment(
            UUID agencyUserId,
            UUID settlementId,
            UpdatePaymentStatusRequest request,
            User actor) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        agencyAccessService.assertAgencyAllowsWrites(agency);
        ShiftSettlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement not found"));
        Shift shift = shiftRepository.findById(settlement.getShiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        if (shift.getAgencyId() == null || !shift.getAgencyId().equals(agency.getId())) {
            throw new ResourceNotFoundException("Settlement not found");
        }
        agencyCaregiverRepository
                .findByAgencyIdAndCaregiverProfileId(agency.getId(), settlement.getCaregiverProfileId())
                .orElseThrow(() -> new BadRequestException("Caregiver is not on this agency roster"));
        return settlementService.markCaregiverPayment(settlementId, request.status(), actor);
    }

    private AgencyRosterScheduleItem toScheduleItem(ShiftClaim claim) {
        Shift shift = claim.getShift();
        int required = Math.max(1, shift.getRequiredHeadcount());
        int filled = Math.max(0, shift.getFilledSlots());
        boolean fulfilled = filled >= required
                || shift.getStatus() == ShiftStatus.COMPLETED
                || claim.getStatus() == ShiftClaimStatus.COMPLETED;
        return new AgencyRosterScheduleItem(
                shift.getId(),
                claim.getId(),
                shift.getDate(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.getCity(),
                shift.getRequiredQualification(),
                shift.getStatus(),
                claim.getStatus(),
                fulfilled,
                filled,
                required,
                scheduleCta(shift, claim, fulfilled));
    }

    private static String scheduleCta(Shift shift, ShiftClaim claim, boolean fulfilled) {
        if (claim.getStatus() == ShiftClaimStatus.PENDING) {
            return "Confirm assignment";
        }
        if (shift.getStatus() == ShiftStatus.IN_PROGRESS) {
            return "Shift in progress — monitor EVV";
        }
        if (shift.getStatus() == ShiftStatus.COMPLETED
                || claim.getStatus() == ShiftClaimStatus.COMPLETED) {
            return "Review timesheet / pay";
        }
        if (!fulfilled) {
            return "Needs coverage (" + shift.getFilledSlots() + "/"
                    + Math.max(1, shift.getRequiredHeadcount()) + " filled)";
        }
        if (shift.getStatus() == ShiftStatus.OPEN || shift.getStatus() == ShiftStatus.CLAIMED) {
            return "Awaiting start";
        }
        if (shift.getStatus() == ShiftStatus.CONFIRMED) {
            return "Ready — confirmed";
        }
        return null;
    }

    private AgencyRosterTimesheetItem toTimesheetItem(ShiftSettlement s) {
        return new AgencyRosterTimesheetItem(
                s.getId(),
                s.getShiftId(),
                s.getShiftDate(),
                s.getHours(),
                s.getPayRate(),
                s.getCaregiverAmount(),
                s.getCaregiverPaymentStatus(),
                s.getClientPaymentStatus(),
                s.getPayPeriodStart(),
                s.getPayPeriodEnd(),
                s.getCaregiverPaidAt(),
                timesheetCta(s.getCaregiverPaymentStatus()));
    }

    private static String timesheetCta(PaymentStatus status) {
        if (status == PaymentStatus.PENDING) {
            return "Mark caregiver paid";
        }
        if (status == PaymentStatus.PROCESSING) {
            return "Mark paid when cleared";
        }
        return null;
    }

    private static AgencyRosterHubSummary summarize(
            List<AgencyRosterScheduleItem> schedule,
            List<AgencyRosterTimesheetItem> timesheets) {
        int upcoming = 0;
        int inProgress = 0;
        int completed = 0;
        int unfulfilled = 0;
        for (AgencyRosterScheduleItem item : schedule) {
            if (!item.fulfilled()) {
                unfulfilled++;
            }
            if (item.shiftStatus() == ShiftStatus.IN_PROGRESS) {
                inProgress++;
            } else if (item.shiftStatus() == ShiftStatus.COMPLETED
                    || item.claimStatus() == ShiftClaimStatus.COMPLETED) {
                completed++;
            } else if (!TERMINAL_SHIFT.contains(item.shiftStatus())) {
                upcoming++;
            }
        }
        int pendingPay = 0;
        BigDecimal pendingAmount = BigDecimal.ZERO;
        for (AgencyRosterTimesheetItem t : timesheets) {
            if (t.caregiverPaymentStatus() != PaymentStatus.PAID) {
                pendingPay++;
                if (t.caregiverAmount() != null) {
                    pendingAmount = pendingAmount.add(t.caregiverAmount());
                }
            }
        }
        return new AgencyRosterHubSummary(
                upcoming, inProgress, completed, unfulfilled, pendingPay, pendingAmount);
    }

    private AgencyCaregiver requireAgencyRosterRow(Agency agency, UUID rosterId) {
        AgencyCaregiver row = agencyCaregiverRepository.findById(rosterId)
                .orElseThrow(() -> new ResourceNotFoundException("Roster entry not found"));
        if (!row.getAgency().getId().equals(agency.getId())) {
            throw new ResourceNotFoundException("Roster entry not found");
        }
        return row;
    }
}
