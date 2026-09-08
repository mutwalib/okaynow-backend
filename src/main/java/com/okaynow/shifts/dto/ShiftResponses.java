package com.okaynow.shifts.dto;

import com.okaynow.users.domain.Role;

/**
 * Role-scoped shift payloads: caregivers see marketplace pay only; clients/facilities
 * see bill only; agency shift pay is never shown to caregivers (roster offer / settlements
 * are separate). Admins see both.
 */
public final class ShiftResponses {

    private ShiftResponses() {
    }

    public static ShiftResponse forViewer(ShiftResponse raw, Role role) {
        if (raw == null || role == null || role == Role.ADMIN) {
            return raw;
        }
        if (role == Role.CAREGIVER) {
            // Agency shifts: pay is between home and agency — never expose to caregivers.
            java.math.BigDecimal pay = raw.agencyId() != null ? null : raw.payRate();
            return copy(
                    raw,
                    pay,
                    null,
                    false);
        }
        if (role == Role.CLIENT || role == Role.FACILITY || role == Role.AGENCY_ADMIN) {
            return copy(
                    raw,
                    null,
                    raw.billRate(),
                    raw.platformPaid());
        }
        return raw;
    }

    private static ShiftResponse copy(
            ShiftResponse raw,
            java.math.BigDecimal payRate,
            java.math.BigDecimal billRate,
            boolean platformPaid) {
        return new ShiftResponse(
                raw.id(),
                raw.clientProfileId(),
                raw.facilityProfileId(),
                raw.requiredQualification(),
                raw.date(),
                raw.startTime(),
                raw.endTime(),
                raw.addressLine(),
                raw.city(),
                raw.state(),
                raw.zip(),
                raw.lat(),
                raw.lng(),
                payRate,
                billRate,
                raw.status(),
                raw.scheduleType(),
                raw.seriesId(),
                raw.notes(),
                platformPaid,
                raw.marketplacePosted(),
                raw.marketplaceSlots(),
                raw.requiredHeadcount(),
                raw.filledSlots(),
                raw.surgeBonusPay(),
                raw.surgeTierApplied(),
                raw.escalationRadiusBonusMiles(),
                raw.createdBy(),
                raw.createdAt(),
                raw.agencyCoverageRequested(),
                raw.agencyId(),
                raw.agencyDisplayName(),
                raw.siteDisplayName());
    }

    /** Attach agency identity without changing pay/bill visibility rules. */
    public static ShiftResponse withAgency(
            ShiftResponse raw,
            java.util.UUID agencyId,
            String agencyDisplayName) {
        if (raw == null) {
            return null;
        }
        return new ShiftResponse(
                raw.id(),
                raw.clientProfileId(),
                raw.facilityProfileId(),
                raw.requiredQualification(),
                raw.date(),
                raw.startTime(),
                raw.endTime(),
                raw.addressLine(),
                raw.city(),
                raw.state(),
                raw.zip(),
                raw.lat(),
                raw.lng(),
                raw.payRate(),
                raw.billRate(),
                raw.status(),
                raw.scheduleType(),
                raw.seriesId(),
                raw.notes(),
                raw.platformPaid(),
                raw.marketplacePosted(),
                raw.marketplaceSlots(),
                raw.requiredHeadcount(),
                raw.filledSlots(),
                raw.surgeBonusPay(),
                raw.surgeTierApplied(),
                raw.escalationRadiusBonusMiles(),
                raw.createdBy(),
                raw.createdAt(),
                raw.agencyCoverageRequested(),
                agencyId,
                agencyDisplayName,
                raw.siteDisplayName());
    }

    public static ShiftResponse withSiteDisplayName(ShiftResponse raw, String siteDisplayName) {
        if (raw == null) {
            return null;
        }
        return new ShiftResponse(
                raw.id(),
                raw.clientProfileId(),
                raw.facilityProfileId(),
                raw.requiredQualification(),
                raw.date(),
                raw.startTime(),
                raw.endTime(),
                raw.addressLine(),
                raw.city(),
                raw.state(),
                raw.zip(),
                raw.lat(),
                raw.lng(),
                raw.payRate(),
                raw.billRate(),
                raw.status(),
                raw.scheduleType(),
                raw.seriesId(),
                raw.notes(),
                raw.platformPaid(),
                raw.marketplacePosted(),
                raw.marketplaceSlots(),
                raw.requiredHeadcount(),
                raw.filledSlots(),
                raw.surgeBonusPay(),
                raw.surgeTierApplied(),
                raw.escalationRadiusBonusMiles(),
                raw.createdBy(),
                raw.createdAt(),
                raw.agencyCoverageRequested(),
                raw.agencyId(),
                raw.agencyDisplayName(),
                siteDisplayName);
    }

    /** Override pay rate (e.g. caregiver's agreed agency roster rate). */
    public static ShiftResponse withPayRate(
            ShiftResponse raw, java.math.BigDecimal payRate) {
        if (raw == null) {
            return null;
        }
        return copy(raw, payRate, raw.billRate(), raw.platformPaid());
    }
}
