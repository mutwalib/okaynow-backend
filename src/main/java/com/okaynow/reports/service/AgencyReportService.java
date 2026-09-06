package com.okaynow.reports.service;

import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.booking.domain.ShiftClaim;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.booking.dto.ShiftClaimResponse;
import com.okaynow.booking.repository.ShiftClaimRepository;
import com.okaynow.booking.service.BookingService;
import com.okaynow.common.dto.PagedResponse;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.payroll.domain.PaymentStatus;
import com.okaynow.payroll.dto.FinanceSummaryResponse;
import com.okaynow.payroll.dto.SettlementResponse;
import com.okaynow.payroll.service.FinanceService;
import com.okaynow.reports.domain.ReportFormat;
import com.okaynow.reports.domain.ReportType;
import com.okaynow.reports.dto.GeneratedReport;
import com.okaynow.reports.dto.ReportMeta;
import com.okaynow.reports.support.ReportWriters;
import com.okaynow.shifts.domain.DayPeriod;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.dto.ShiftResponse;
import com.okaynow.shifts.mapper.ShiftMapper;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.shifts.repository.ShiftSpecifications;
import com.okaynow.shifts.service.ShiftAgencyLabelService;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.FacilityProfile;
import com.okaynow.users.domain.Qualification;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.FacilityProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Agency-tenant Excel/PDF reports (finance, shifts, claims) scoped to the caller's agency.
 */
@Service
@RequiredArgsConstructor
public class AgencyReportService {

    private static final int EXPORT_SIZE = 5_000;
    private static final EnumSet<ReportType> ALLOWED = EnumSet.of(
            ReportType.FINANCE, ReportType.SHIFTS, ReportType.CLAIMS);

    private final AgencyAccessService agencyAccessService;
    private final FinanceService financeService;
    private final BookingService bookingService;
    private final ShiftRepository shiftRepository;
    private final ShiftMapper shiftMapper;
    private final ShiftAgencyLabelService shiftAgencyLabelService;
    private final ShiftClaimRepository shiftClaimRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;

    @Transactional
    public GeneratedReport generate(
            UUID agencyUserId,
            ReportType type,
            ReportFormat format,
            User actor,
            Map<String, String> rawFilters) throws Exception {
        if (!ALLOWED.contains(type)) {
            throw new BadRequestException("Report type not available for agencies: " + type);
        }
        UUID agencyId = agencyAccessService.requireAgencyForUser(agencyUserId).getId();
        return switch (type) {
            case FINANCE -> finance(agencyId, format, actor, rawFilters);
            case SHIFTS -> shifts(agencyId, format, actor, rawFilters);
            case CLAIMS -> claims(agencyId, format, actor, rawFilters);
            default -> throw new BadRequestException("Report type not available for agencies");
        };
    }

    private GeneratedReport finance(
            UUID agencyId, ReportFormat format, User actor, Map<String, String> raw)
            throws Exception {
        LocalDate periodStart = parseDate(raw.get("periodStart"));
        LocalDate periodEnd = parseDate(raw.get("periodEnd"));
        PaymentStatus clientStatus = parseEnum(raw.get("clientPaymentStatus"), PaymentStatus.class);
        PaymentStatus caregiverStatus = parseEnum(raw.get("caregiverPaymentStatus"), PaymentStatus.class);

        FinanceSummaryResponse summary = financeService.summaryForAgency(agencyId, periodStart, periodEnd);
        PagedResponse<SettlementResponse> page = financeService.listSettlementsForAgency(
                agencyId, periodStart, periodEnd, clientStatus, caregiverStatus, null,
                PageRequest.of(0, EXPORT_SIZE, Sort.by(Sort.Direction.DESC, "shiftDate")));

        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("Period", summary.periodStart() + " → " + summary.periodEnd());
        filters.put("Client payment", label(clientStatus));
        filters.put("Caregiver payment", label(caregiverStatus));
        filters.put("Completed shifts", String.valueOf(summary.completedShifts()));
        filters.put("Client billed", money(summary.clientBilled()));
        filters.put("Caregiver owed", money(summary.caregiverOwed()));
        filters.put("Agency margin", money(summary.agencyMarginAccrued()));

        List<String> headers = List.of(
                "Shift date", "Client", "Caregiver", "Hours", "Client $", "Caregiver $",
                "Agency $", "Client status", "Caregiver status", "Pay period");
        List<List<String>> rows = new ArrayList<>();
        for (SettlementResponse s : page.content()) {
            String clientName = s.facilityName() != null && !s.facilityName().isBlank()
                    ? s.facilityName()
                    : name(s.clientFirstName(), s.clientLastName());
            rows.add(List.of(
                    str(s.shiftDate()),
                    clientName,
                    name(s.caregiverFirstName(), s.caregiverLastName()),
                    str(s.hours()),
                    money(s.clientAmount()),
                    money(s.caregiverAmount()),
                    money(s.agencyAmount()),
                    str(s.clientPaymentStatus()),
                    str(s.caregiverPaymentStatus()),
                    s.payPeriodStart() + " → " + s.payPeriodEnd()));
        }
        return write(ReportType.FINANCE, format, ReportMeta.of(
                "Agency finance & settlements report", actor.getEmail(), filters), headers, rows);
    }

    private GeneratedReport shifts(
            UUID agencyId, ReportFormat format, User actor, Map<String, String> raw)
            throws Exception {
        ShiftStatus status = parseEnum(raw.get("status"), ShiftStatus.class);
        Qualification qualification = parseEnum(raw.get("qualification"), Qualification.class);
        LocalDate dateFrom = parseDate(raw.get("dateFrom"));
        LocalDate dateTo = parseDate(raw.get("dateTo"));
        UUID clientId = parseUuid(raw.get("clientProfileId"));
        UUID facilityId = parseUuid(raw.get("facilityProfileId"));
        BigDecimal minPay = parseDecimal(raw.get("minPay"));
        BigDecimal maxPay = parseDecimal(raw.get("maxPay"));
        DayPeriod dayPeriod = parseEnum(raw.get("dayPeriod"), DayPeriod.class);

        Specification<Shift> filters = ShiftSpecifications.forAgency(agencyId)
                .and(ShiftSpecifications.withFilters(
                        status, qualification, dateFrom, dateTo, clientId, facilityId,
                        minPay, maxPay, "payRate", dayPeriod));
        Page<Shift> page = shiftRepository.findAll(
                filters, PageRequest.of(0, EXPORT_SIZE, Sort.by("date", "startTime")));

        List<ShiftResponse> shiftResponses = page.getContent().stream()
                .map(s -> shiftAgencyLabelService.label(s, shiftMapper.toResponse(s)))
                .toList();
        ClientNameIndex names = loadClientNames(shiftResponses);
        Map<UUID, CaregiverCells> caregiversByShift = loadActiveCaregiversByShift(
                shiftResponses.stream().map(ShiftResponse::id).toList());

        Map<String, String> filterLabels = new LinkedHashMap<>();
        filterLabels.put("Status", label(status));
        filterLabels.put("Qualification", label(qualification));
        filterLabels.put("Date from", label(dateFrom));
        filterLabels.put("Date to", label(dateTo));
        filterLabels.put("Family client", clientId != null ? names.client(clientId) : "All");
        filterLabels.put("Facility client", facilityId != null ? names.facility(facilityId) : "All");
        filterLabels.put("Min pay", label(minPay));
        filterLabels.put("Max pay", label(maxPay));
        filterLabels.put("Day period", label(dayPeriod));

        List<String> headers = List.of(
                "Date", "Start", "End", "Status", "Qualification", "Client",
                "Caregiver", "City", "ZIP",
                "Pay rate", "Bill rate", "Platform paid");
        List<List<String>> rows = new ArrayList<>();
        for (ShiftResponse s : shiftResponses) {
            CaregiverCells cg = caregiversByShift.getOrDefault(s.id(), CaregiverCells.EMPTY);
            rows.add(List.of(
                    str(s.date()),
                    str(s.startTime()),
                    str(s.endTime()),
                    str(s.status()),
                    str(s.requiredQualification()),
                    names.forShift(s),
                    cg.names(),
                    str(s.city()),
                    str(s.zip()),
                    money(s.payRate()),
                    money(s.billRate()),
                    s.platformPaid() ? "PAID" : "UNPAID"));
        }
        return write(ReportType.SHIFTS, format, ReportMeta.of(
                "Agency shifts report", actor.getEmail(), filterLabels), headers, rows);
    }

    private GeneratedReport claims(
            UUID agencyId, ReportFormat format, User actor, Map<String, String> raw)
            throws Exception {
        ShiftClaimStatus status = parseEnum(raw.get("status"), ShiftClaimStatus.class);
        PagedResponse<ShiftClaimResponse> page = bookingService.claimsForAgency(
                agencyId, status, PageRequest.of(0, EXPORT_SIZE, Sort.by(Sort.Direction.DESC, "claimedAt")));

        List<ShiftResponse> shifts = page.content().stream()
                .map(ShiftClaimResponse::shift)
                .filter(Objects::nonNull)
                .toList();
        ClientNameIndex names = loadClientNames(shifts);

        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("Claim status", label(status));

        List<String> headers = List.of(
                "Claimed at", "Claim status", "Source", "Client", "Caregiver",
                "Shift date", "Shift status", "Pay rate", "Bill rate");
        List<List<String>> rows = new ArrayList<>();
        for (ShiftClaimResponse c : page.content()) {
            ShiftResponse s = c.shift();
            rows.add(List.of(
                    str(c.claimedAt()),
                    str(c.status()),
                    str(c.source()),
                    s != null ? names.forShift(s) : "",
                    name(c.caregiverFirstName(), c.caregiverLastName()),
                    s != null ? str(s.date()) : "",
                    s != null ? str(s.status()) : "",
                    s != null ? money(s.payRate()) : "",
                    s != null ? money(s.billRate()) : ""));
        }
        return write(ReportType.CLAIMS, format, ReportMeta.of(
                "Agency claims report", actor.getEmail(), filters), headers, rows);
    }

    private ClientNameIndex loadClientNames(List<ShiftResponse> shifts) {
        Set<UUID> clientIds = new HashSet<>();
        Set<UUID> facilityIds = new HashSet<>();
        for (ShiftResponse s : shifts) {
            if (s.clientProfileId() != null) {
                clientIds.add(s.clientProfileId());
            }
            if (s.facilityProfileId() != null) {
                facilityIds.add(s.facilityProfileId());
            }
        }
        return new ClientNameIndex(
                loadFamilyClientNames(clientIds),
                loadFacilityNames(facilityIds));
    }

    private Map<UUID, CaregiverCells> loadActiveCaregiversByShift(List<UUID> shiftIds) {
        if (shiftIds.isEmpty()) {
            return Map.of();
        }
        List<ShiftClaim> claims = shiftClaimRepository.findByShiftIdInAndStatusIn(
                shiftIds,
                EnumSet.of(ShiftClaimStatus.PENDING, ShiftClaimStatus.CONFIRMED));
        Map<UUID, List<String>> names = new LinkedHashMap<>();
        for (ShiftClaim claim : claims) {
            UUID shiftId = claim.getShift().getId();
            var cg = claim.getCaregiverProfile();
            if (cg == null) {
                continue;
            }
            String display = name(cg.getFirstName(), cg.getLastName());
            names.computeIfAbsent(shiftId, ignored -> new ArrayList<>()).add(display);
        }
        Map<UUID, CaregiverCells> out = new HashMap<>();
        for (Map.Entry<UUID, List<String>> entry : names.entrySet()) {
            out.put(entry.getKey(), new CaregiverCells(String.join("; ", entry.getValue())));
        }
        return out;
    }

    private Map<UUID, String> loadFamilyClientNames(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> out = new HashMap<>();
        for (ClientProfile c : clientProfileRepository.findAllById(ids)) {
            out.put(c.getId(), name(c.getFirstName(), c.getLastName()));
        }
        return out;
    }

    private Map<UUID, String> loadFacilityNames(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> out = new HashMap<>();
        for (FacilityProfile f : facilityProfileRepository.findAllById(ids)) {
            out.put(f.getId(), f.getFacilityName() != null ? f.getFacilityName() : "");
        }
        return out;
    }

    private record CaregiverCells(String names) {
        static final CaregiverCells EMPTY = new CaregiverCells("");
    }

    private record ClientNameIndex(Map<UUID, String> clients, Map<UUID, String> facilities) {
        String forShift(ShiftResponse s) {
            if (s.facilityProfileId() != null) {
                String facility = facilities.get(s.facilityProfileId());
                if (facility != null && !facility.isBlank()) {
                    return facility;
                }
            }
            if (s.clientProfileId() != null) {
                return clients.getOrDefault(s.clientProfileId(), "");
            }
            return "";
        }

        String client(UUID id) {
            return clients.getOrDefault(id, str(id));
        }

        String facility(UUID id) {
            return facilities.getOrDefault(id, str(id));
        }
    }

    private GeneratedReport write(
            ReportType type,
            ReportFormat format,
            ReportMeta meta,
            List<String> headers,
            List<List<String>> rows) throws Exception {
        byte[] bytes = format == ReportFormat.PDF
                ? ReportWriters.pdf(meta, headers, rows)
                : ReportWriters.excel(meta, headers, rows);
        String filename = "okaynow-agency-" + type.name().toLowerCase() + "-"
                + LocalDate.now() + "." + format.extension();
        return new GeneratedReport(filename, format.contentType(), bytes);
    }

    private static String money(BigDecimal value) {
        if (value == null) return "";
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String name(String first, String last) {
        return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String label(Object value) {
        return value == null ? "All" : String.valueOf(value);
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return LocalDate.parse(raw);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return UUID.fromString(raw);
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return new BigDecimal(raw);
    }

    private static <E extends Enum<E>> E parseEnum(String raw, Class<E> type) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid " + type.getSimpleName() + ": " + raw);
        }
    }
}
