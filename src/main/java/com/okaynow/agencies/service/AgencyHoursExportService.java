package com.okaynow.agencies.service;

import com.okaynow.agencies.dto.AgencyHoursExportRow;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.payroll.domain.ShiftSettlement;
import com.okaynow.payroll.repository.ShiftSettlementRepository;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.repository.ClientProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CSV hours/pay export for agencies to feed into their own payroll (Gusto/ADP/etc.).
 * OkayNow does not run W-2 or payroll disbursement.
 */
@Service
@RequiredArgsConstructor
public class AgencyHoursExportService {

    private final AgencyAccessService agencyAccessService;
    private final ShiftRepository shiftRepository;
    private final ShiftSettlementRepository settlementRepository;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final ClientProfileRepository clientProfileRepository;

    @Transactional(readOnly = true)
    public List<AgencyHoursExportRow> rows(UUID agencyUserId, LocalDate from, LocalDate to) {
        UUID agencyId = agencyAccessService.requireAgencyForUser(agencyUserId).getId();
        if (from == null || to == null || to.isBefore(from)) {
            throw new BadRequestException("Provide a valid from/to date range");
        }
        List<Shift> shifts = shiftRepository.findByAgencyIdOrderByDateDescStartTimeDesc(agencyId).stream()
                .filter(s -> s.getDate() != null
                        && !s.getDate().isBefore(from)
                        && !s.getDate().isAfter(to))
                .toList();
        if (shifts.isEmpty()) {
            return List.of();
        }
        Map<UUID, Shift> byId = shifts.stream()
                .collect(Collectors.toMap(Shift::getId, Function.identity()));
        List<ShiftSettlement> settlements = new ArrayList<>();
        for (Shift shift : shifts) {
            settlementRepository.findByShiftId(shift.getId()).ifPresent(settlements::add);
        }
        Map<UUID, CaregiverProfile> caregivers = caregiverProfileRepository.findAllById(
                        settlements.stream().map(ShiftSettlement::getCaregiverProfileId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(CaregiverProfile::getId, Function.identity()));
        Map<UUID, ClientProfile> clients = clientProfileRepository.findAllById(
                        settlements.stream()
                                .map(ShiftSettlement::getClientProfileId)
                                .filter(id -> id != null)
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(ClientProfile::getId, Function.identity()));

        List<AgencyHoursExportRow> rows = new ArrayList<>();
        for (ShiftSettlement s : settlements) {
            Shift shift = byId.get(s.getShiftId());
            CaregiverProfile cg = caregivers.get(s.getCaregiverProfileId());
            ClientProfile client = s.getClientProfileId() != null
                    ? clients.get(s.getClientProfileId())
                    : null;
            rows.add(new AgencyHoursExportRow(
                    s.getShiftDate() != null ? s.getShiftDate().toString() : "",
                    cg != null ? (cg.getFirstName() + " " + cg.getLastName()).trim() : "",
                    cg != null && cg.getUser() != null ? cg.getUser().getEmail() : "",
                    client != null ? (client.getFirstName() + " " + client.getLastName()).trim() : "",
                    shift != null && shift.getRequiredQualification() != null
                            ? shift.getRequiredQualification().name()
                            : "",
                    money(s.getHours()),
                    money(s.getPayRate()),
                    money(s.getCaregiverAmount()),
                    money(s.getBillRate()),
                    money(s.getClientAmount()),
                    s.getShiftId().toString()));
        }
        rows.sort(Comparator.comparing(AgencyHoursExportRow::shiftDate)
                .thenComparing(AgencyHoursExportRow::caregiverName));
        return rows;
    }

    @Transactional(readOnly = true)
    public byte[] csv(UUID agencyUserId, LocalDate from, LocalDate to) {
        List<AgencyHoursExportRow> rows = rows(agencyUserId, from, to);
        StringBuilder sb = new StringBuilder();
        sb.append("shift_date,caregiver_name,caregiver_email,client_name,qualification,")
                .append("hours,pay_rate,caregiver_amount,bill_rate,client_amount,shift_id\n");
        for (AgencyHoursExportRow r : rows) {
            sb.append(csv(r.shiftDate())).append(',')
                    .append(csv(r.caregiverName())).append(',')
                    .append(csv(r.caregiverEmail())).append(',')
                    .append(csv(r.clientName())).append(',')
                    .append(csv(r.qualification())).append(',')
                    .append(csv(r.hours())).append(',')
                    .append(csv(r.payRate())).append(',')
                    .append(csv(r.caregiverAmount())).append(',')
                    .append(csv(r.billRate())).append(',')
                    .append(csv(r.clientAmount())).append(',')
                    .append(csv(r.shiftId())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String money(BigDecimal value) {
        return value == null ? "0.00" : value.toPlainString();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
